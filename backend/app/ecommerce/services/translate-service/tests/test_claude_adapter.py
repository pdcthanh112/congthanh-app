"""Adapter Claude với client giả: kiểm request gửi đi và cách xử lý từng stop_reason."""

import json
from types import SimpleNamespace

import pytest

from app.adapters.claude import ClaudeTranslator, TranslatorUnavailable
from app.domain.translation import Format, TranslateRequest


class FakeMessages:
    def __init__(self, response):
        self.response, self.kwargs = response, None

    async def create(self, **kwargs):
        self.kwargs = kwargs
        return self.response


def client(text, stop="end_turn"):
    msgs = FakeMessages(SimpleNamespace(stop_reason=stop, content=[SimpleNamespace(type="text", text=text)]))
    return SimpleNamespace(beta=SimpleNamespace(messages=msgs)), msgs


async def test_request_shape_and_parse():
    c, msgs = client(json.dumps({"translations": ["T-shirt", "<b>Jeans</b>"]}))
    out = await ClaudeTranslator(c, "claude-opus-5-5", "low").translate(
        TranslateRequest(texts=(), target="en", source="vi", format=Format.HTML), ["Áo thun", "<b>Quần</b>"]
    )
    assert out == ["T-shirt", "<b>Jeans</b>"]
    k = msgs.kwargs
    assert k["model"] == "claude-opus-5-5" and k["fallbacks"] == "default"
    assert k["output_config"]["format"]["type"] == "json_schema" and k["output_config"]["effort"] == "low"
    assert "server-side-fallback-2026-07-01" in k["betas"]
    prompt = k["messages"][0]["content"]
    assert "Vietnamese" in prompt and "English" in prompt and "HTML" in prompt and '"Áo thun"' in prompt


@pytest.mark.parametrize(
    ("text", "stop"),
    [
        ("{}", "refusal"),
        ("{}", "max_tokens"),
        ("not json", "end_turn"),
        (json.dumps({"translations": ["only one"]}), "end_turn"),
    ],
)
async def test_failures(text, stop):
    c, _ = client(text, stop)
    with pytest.raises(TranslatorUnavailable):
        await ClaudeTranslator(c, "m", "low").translate(TranslateRequest(texts=(), target="en"), ["a", "b"])
