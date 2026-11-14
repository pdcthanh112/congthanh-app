"""Chính sách kiểm duyệt dạng luật (tầng rẻ, chạy cho mọi nội dung). Thuần, không I/O.

Nguyên tắc pháp lý (Mỹ):
- FTC (quy định 2024 về đánh giá giả, Consumer Review Fairness Act): KHÔNG được ẩn đánh giá chỉ vì tiêu cực.
  Luật ở đây không xét cảm xúc hay số sao; chỉ xét thông tin cá nhân, spam/quảng cáo, từ cấm, lách sàn.
- Section 230 bảo vệ quyết định kiểm duyệt có thiện chí; lưu lý do và phiên bản chính sách cho mọi quyết định.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from enum import StrEnum


class ContentType(StrEnum):
    REVIEW = "REVIEW"
    MESSAGE = "MESSAGE"
    PRODUCT_LISTING = "PRODUCT_LISTING"
    SUPPLIER_PROFILE = "SUPPLIER_PROFILE"


class Decision(StrEnum):
    APPROVE = "APPROVE"
    REJECT = "REJECT"
    REDACT = "REDACT"  # hiện bản đã che phần vi phạm
    ESCALATE = "ESCALATE"  # chờ người duyệt


class Category(StrEnum):
    PERSONAL_INFO = "PERSONAL_INFO"  # số điện thoại, email
    OFF_PLATFORM = "OFF_PLATFORM"  # kéo giao dịch ra ngoài sàn (link, WhatsApp...)
    SPAM = "SPAM"
    PROFANITY = "PROFANITY"
    HATE_HARASSMENT = "HATE_HARASSMENT"
    SEXUAL = "SEXUAL"
    PROHIBITED_ITEM = "PROHIBITED_ITEM"
    FAKE_REVIEW = "FAKE_REVIEW"
    OTHER = "OTHER"


PHONE = re.compile(r"(?<!\d)(?:\+?1[\s.-]?)?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}(?!\d)")
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
URL = re.compile(r"(?:https?://|www\.)\S+|\b[a-z0-9-]+\.(?:com|net|org|io|shop|store|co|me|ly)(?:/\S*)?\b", re.I)
OFF_PLATFORM_WORDS = re.compile(r"\b(whats\s?app|telegram|wechat|venmo|cash\s?app|zelle|text me|dm me)\b", re.I)
REPEATED = re.compile(r"(.)\1{9,}")
# Danh sách mặc định cố ý ngắn; danh sách đầy đủ do Trust & Safety cấu hình qua BANNED_TERMS.
DEFAULT_BANNED = ("counterfeit", "replica watch", "fake id")
PROHIBITED = re.compile(r"\b(counterfeit|replica watch|fake id|unlicensed firearm)\b", re.I)


@dataclass(frozen=True)
class Item:
    content_type: ContentType
    content_id: str
    author_id: str
    text: str


@dataclass
class Verdict:
    decision: Decision
    categories: list[Category] = field(default_factory=list)
    redacted_text: str = ""
    rationale: str = ""
    # True khi luật không đủ chắc: nên hỏi tầng LLM (nếu bật) trước khi duyệt
    uncertain: bool = False


def _mask(pattern: re.Pattern, text: str, label: str) -> str:
    return pattern.sub(f"[{label} removed]", text)


def evaluate(item: Item, banned_terms: tuple[str, ...] = ()) -> Verdict:
    text = item.text or ""
    lowered = text.lower()
    cats: list[Category] = []
    redacted = text

    if PHONE.search(text) or EMAIL.search(text):
        cats.append(Category.PERSONAL_INFO)
        redacted = _mask(EMAIL, _mask(PHONE, redacted, "phone"), "email")
    links = URL.findall(text)
    if links or OFF_PLATFORM_WORDS.search(text):
        cats.append(Category.OFF_PLATFORM)
        redacted = _mask(URL, redacted, "link")
    terms = tuple(t.strip().lower() for t in (*DEFAULT_BANNED, *banned_terms) if t.strip())
    if PROHIBITED.search(text):
        cats.append(Category.PROHIBITED_ITEM)
    elif any(t in lowered for t in terms):
        cats.append(Category.PROFANITY)
    letters = [c for c in text if c.isalpha()]
    shouting = len(letters) >= 30 and sum(c.isupper() for c in letters) / len(letters) > 0.8
    if REPEATED.search(text) or len(links) >= 3 or shouting:
        cats.append(Category.SPAM)

    if Category.PROHIBITED_ITEM in cats:
        # listing hàng cấm cần người xác nhận (có thể là cảnh báo "không phải hàng giả")
        return Verdict(Decision.ESCALATE, cats, "", "possible prohibited item")
    if item.content_type == ContentType.MESSAGE:
        # chat thời gian thực: che ngay, không chặn hội thoại
        if Category.PERSONAL_INFO in cats or Category.OFF_PLATFORM in cats:
            return Verdict(Decision.REDACT, cats, redacted, "contact details are hidden in chat")
        if Category.SPAM in cats or Category.PROFANITY in cats:
            return Verdict(Decision.ESCALATE, cats, "", "possible abuse in chat")
        return Verdict(Decision.APPROVE, cats)
    if Category.SPAM in cats and Category.OFF_PLATFORM in cats:
        return Verdict(Decision.REJECT, cats, "", "promotional spam with outside links")
    if Category.PROFANITY in cats:
        return Verdict(Decision.ESCALATE, cats, "", "banned term")
    if Category.PERSONAL_INFO in cats or Category.OFF_PLATFORM in cats:
        if item.content_type == ContentType.REVIEW:
            return Verdict(Decision.REDACT, cats, redacted, "personal or outside contact details removed")
        return Verdict(Decision.ESCALATE, cats, "", "listing points buyers off the platform")
    if Category.SPAM in cats:
        return Verdict(Decision.ESCALATE, cats, "", "looks like spam")
    # Không có dấu hiệu rõ: đánh giá/listing dài thì nên cho tầng LLM xem (quấy rối, thù ghét, đánh giá giả).
    return Verdict(Decision.APPROVE, cats, "", "no rule matched", uncertain=len(text) >= 40)


def merge_llm(rule: Verdict, llm: Verdict | None) -> Verdict:
    """Kết hợp với tầng LLM: LLM chỉ được làm quyết định CHẶT hơn hoặc đưa vào hàng chờ, không nới quyết định của luật."""
    if llm is None:
        return rule
    order = {Decision.APPROVE: 0, Decision.REDACT: 1, Decision.ESCALATE: 2, Decision.REJECT: 3}
    if order[llm.decision] <= order[rule.decision]:
        return rule
    # LLM nói REJECT với độ chắc chưa được kiểm chứng: đưa người duyệt thay vì từ chối thẳng.
    decision = Decision.ESCALATE if llm.decision == Decision.REJECT else llm.decision
    cats = list(dict.fromkeys([*rule.categories, *llm.categories]))
    return Verdict(decision, cats, llm.redacted_text or rule.redacted_text, "llm: " + llm.rationale)
