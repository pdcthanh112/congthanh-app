import pytest

from app.domain.policy import Category, Decision, Verdict
from app.service import Forbidden, Invalid, ModerationService, NotFound


class FakeStore:
    def __init__(self):
        self.items = {}
        self.strikes = []
        self.rejects = 0

    async def cached_verdict(self, h):
        for it in self.items.values():
            if it["hash"] == h and it["status"] == "DECIDED":
                return {"decision": it["decision"], "categories": it["categories"], "redacted_text": it["redactedText"]}
        return None

    async def save_item(self, item, h, verdict, by):
        iid = f"i-{len(self.items) + 1}"
        self.items[iid] = {
            "id": iid,
            "hash": h,
            "authorId": item.author_id,
            "decision": verdict.decision.value,
            "categories": [str(c) for c in verdict.categories],
            "redactedText": verdict.redacted_text,
            "status": "ESCALATED" if verdict.decision == Decision.ESCALATE else "DECIDED",
            "decidedBy": by,
        }
        if verdict.decision == Decision.REJECT:
            self.rejects += 1
        return self.items[iid]

    async def get(self, iid):
        return self.items.get(iid)

    async def decide(self, iid, verdict, by, note):
        it = self.items[iid]
        if it["status"] not in ("ESCALATED", "APPEALED"):
            return None
        it.update(status="DECIDED", decision=verdict.decision.value, decidedBy=by)
        return it

    async def appeal(self, iid, author, reason):
        it = self.items[iid]
        if it["decision"] not in ("REJECT", "REDACT") or it["status"] != "DECIDED":
            return None
        it["status"] = "APPEALED"
        return it

    async def rejections_since(self, author, days):
        return self.rejects

    async def issue_strike(self, author, n, iid):
        self.strikes.append((author, n))


class FlaggingLLM:
    def __init__(self):
        self.calls = 0

    async def classify(self, item):
        self.calls += 1
        return Verdict(Decision.REJECT, [Category.HATE_HARASSMENT], "", "harassment")


TEXT = "This seller is a total idiot and everyone should harass them until they quit."


async def test_uncertain_review_goes_to_llm_once_then_cache_is_reused():
    llm = FlaggingLLM()
    svc = ModerationService(FakeStore(), llm)
    first = await svc.submit("review", "r-1", "c-1", TEXT)
    assert first["decision"] == "ESCALATE"
    # người duyệt xử lý; lần dán lại cùng nội dung dùng quyết định đã có, không gọi LLM nữa
    await svc.decide(first["id"], "REJECT", "mod-1", "harassment")
    again = await svc.submit("REVIEW", "r-2", "c-1", TEXT)
    assert again["decision"] == "REJECT"
    assert llm.calls == 1


async def test_chat_never_waits_for_llm():
    llm = FlaggingLLM()
    svc = ModerationService(FakeStore(), llm)
    await svc.submit("MESSAGE", "m-1", "c-1", TEXT)
    assert llm.calls == 0


async def test_repeated_rejections_issue_a_strike():
    store = FakeStore()
    svc = ModerationService(store, None, strike_threshold=2)
    for i in range(2):
        await svc.submit("REVIEW", f"r-{i}", "c-9", f"spam {i} visit a.com b.com c.com now")
    assert store.strikes == [("c-9", 2)]


async def test_appeal_rules():
    store = FakeStore()
    svc = ModerationService(store, None)
    rejected = await svc.submit("REVIEW", "r-1", "c-1", "deals deals a.com b.com c.com")
    with pytest.raises(NotFound):
        await svc.appeal(rejected["id"], "someone-else", "mine")
    appealed = await svc.appeal(rejected["id"], "c-1", "it was a joke")
    assert appealed["status"] == "APPEALED"
    store.items[rejected["id"]]["decidedBy"] = "mod-1"
    with pytest.raises(Forbidden):
        await svc.decide(rejected["id"], "APPROVE", "mod-1", "ok")
    assert (await svc.decide(rejected["id"], "APPROVE", "mod-2", "ok"))["decision"] == "APPROVE"


async def test_input_validation():
    svc = ModerationService(FakeStore(), None)
    with pytest.raises(Invalid):
        await svc.submit("TWEET", "x", "c", "hi")
    with pytest.raises(Invalid):
        await svc.submit("REVIEW", "x", "c", "a" * 20_001)
    item = await svc.submit("REVIEW", "x", "c", "short and fine")
    with pytest.raises(Invalid):
        await svc.decide(item["id"], "ESCALATE", "m", "n")
