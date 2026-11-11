from app.domain.embedding import HashingEmbedder, cosine, strip_accents, tokens, weighted_mean
from app.domain.ranking import Reason, Scored, blend, decay, pair_score


def test_tokens_cover_accents_and_bigrams():
    t = tokens("Café Crème Mug")
    assert {"café", "crème", "mug", "cafe", "creme", "cafe_creme", "creme_mug"} <= set(t)
    assert strip_accents("Jalapeño Brûlée") == "Jalapeno Brulee"


def test_similar_names_are_closer_than_unrelated():
    e = HashingEmbedder(256)
    tee_black = e.embed_one("Black Cotton Tee", "c-tee")
    tee_white = e.embed_one("white cotton tee", "c-tee")
    phone = e.embed_one("Galaxy Phone 256GB", "c-phone")
    assert cosine(tee_black, tee_white) > cosine(tee_black, phone)
    assert abs(sum(x * x for x in tee_black) - 1.0) < 1e-9


def test_weighted_mean_is_normalized_and_weighted():
    v = weighted_mean([([1.0, 0.0], 3.0), ([0.0, 1.0], 1.0)])
    assert v[0] > v[1] and abs(v[0] ** 2 + v[1] ** 2 - 1) < 1e-9
    assert weighted_mean([]) is None


def test_pair_score_penalizes_bestsellers():
    niche = pair_score(5, 10, 10)
    bestseller = pair_score(5, 10, 1000)
    assert niche > bestseller > 0
    assert pair_score(0, 1, 1) == 0


def test_decay_half_life():
    assert decay(0) == 1 and abs(decay(72) - 0.5) < 1e-9


def test_blend_excludes_seen_diversifies_and_keeps_reason():
    co = [Scored(f"a{i}", 10 - i, Reason.FOR_YOU, "shirts") for i in range(5)]
    trend = [Scored("t1", 100, Reason.TRENDING, "shoes"), Scored("a0", 50, Reason.TRENDING, "shirts")]
    out = blend([(co, 0.5), (trend, 0.15)], exclude={"a1"}, limit=5, per_category=3)
    ids = [s.product_id for s in out]
    assert "a1" not in ids and ids[0] == "a0"
    assert ids[:4] == ["a0", "a2", "a3", "t1"]  # danh mục shirts chỉ 3 món trước khi lấy thêm danh mục khác
    assert ids[4] == "a4"  # thiếu thì lấy lại phần bị giới hạn
    assert out[0].reason == Reason.FOR_YOU
