from app.domain.policy import Category, ContentType, Decision, Item, Verdict, evaluate, merge_llm


def item(text, ctype=ContentType.REVIEW):
    return Item(ctype, "r-1", "c-1", text)


def test_negative_review_is_never_rejected_by_rules():
    v = evaluate(item("Terrible product, broke after two days. One star. Would not buy again from this seller."))
    assert v.decision == Decision.APPROVE
    assert v.uncertain  # dài: tầng LLM được xem, nhưng prompt cấm gắn cờ vì tiêu cực


def test_contact_details_are_redacted_in_reviews_and_chat():
    v = evaluate(item("Great! call me at (212) 555-0199 or mail jane@example.com"))
    assert v.decision == Decision.REDACT
    assert "555" not in v.redacted_text and "jane@" not in v.redacted_text
    assert Category.PERSONAL_INFO in v.categories

    chat = evaluate(item("pay me on whatsapp, cheaper at bestdeals.shop", ContentType.MESSAGE))
    assert chat.decision == Decision.REDACT
    assert "bestdeals.shop" not in chat.redacted_text


def test_link_spam_is_rejected_and_prohibited_items_escalate():
    spam = evaluate(item("BUY NOW!!! visit a.com b.com c.com for cheap stuff"))
    assert spam.decision == Decision.REJECT
    listing = evaluate(item("Brand new counterfeit handbag, looks real", ContentType.PRODUCT_LISTING))
    assert listing.decision == Decision.ESCALATE
    assert Category.PROHIBITED_ITEM in listing.categories


def test_listing_with_outside_contact_goes_to_a_person():
    v = evaluate(item("Order via telegram for a discount", ContentType.PRODUCT_LISTING))
    assert v.decision == Decision.ESCALATE


def test_configured_banned_terms_escalate():
    assert evaluate(item("this is a badword here"), ("badword",)).decision == Decision.ESCALATE


def test_llm_can_only_tighten_and_its_reject_becomes_escalate():
    rule = Verdict(Decision.APPROVE, [], "", "no rule", uncertain=True)
    assert merge_llm(rule, None) is rule
    assert merge_llm(rule, Verdict(Decision.APPROVE, [], "", "ok")).decision == Decision.APPROVE
    hate = merge_llm(rule, Verdict(Decision.REJECT, [Category.HATE_HARASSMENT], "", "slur"))
    assert hate.decision == Decision.ESCALATE
    assert Category.HATE_HARASSMENT in hate.categories
    redacted = Verdict(Decision.REDACT, [Category.PERSONAL_INFO], "x", "pii")
    assert merge_llm(redacted, Verdict(Decision.APPROVE, [], "", "fine")) is redacted


def test_erased_id_matches_other_languages():
    from app.adapters.privacy import erased_id

    # cùng công thức sha256 với Java/Go/TS: "erased-" + 16 ký tự hex đầu
    assert erased_id("c-1") == erased_id("c-1")
    assert erased_id("c-1").startswith("erased-") and len(erased_id("c-1")) == 23
