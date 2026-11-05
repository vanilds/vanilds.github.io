from decimal import Decimal

import pytest

from creditdesk_worker import policy
from creditdesk_worker.messages import Recommendation, RiskLevel


def codes(assessment):
    return {flag.code: flag.severity for flag in assessment.flags}


@pytest.mark.parametrize(("amount", "rate", "n", "expected"), [
    # The same reference cases as PriceTableTest.java: both services must agree to the cent
    ("15000.00", "0.0199", 24, "792.17"),
    ("250000.00", "0.0149", 60, "6332.05"),
    ("18000.00", "0.0189", 36, "693.78"),
    ("1000.00", "0.02", 1, "1020.00"),
    ("1000.00", "0", 3, "333.33"),
])
def test_installment_matches_the_api(amount, rate, n, expected):
    assert policy.installment(Decimal(amount), Decimal(rate), n) == Decimal(expected)


def test_healthy_proposal_is_favorable(submission):
    a = policy.assess(submission())
    assert a.flags == []
    assert a.risk_level is RiskLevel.LOW
    assert a.recommendation is Recommendation.FAVORABLE
    assert a.suggested_plan is None
    assert a.indicators["installmentToIncome"] == 0.132  # 1,056.22 / 8,000
    assert a.indicators["relationshipMonths"] == 45


def test_installment_above_the_limit_suggests_the_shortest_plan_that_fits(submission):
    # R$ 1,056.22 on R$ 3,000 of income = 35%: above 30%, below 40% → MEDIUM
    a = policy.assess(submission(customer={"monthlyRevenue": "3000.00"}))
    assert codes(a) == {"HIGH_INSTALLMENT_TO_INCOME": RiskLevel.MEDIUM}
    assert a.recommendation is Recommendation.CAUTION
    plan = a.suggested_plan
    assert plan is not None and plan.installments == 30
    assert plan.installment_to_income <= Decimal("0.30")
    # 24 < 30 and the 30× plan is the first candidate that fits
    assert policy.installment(Decimal("20000.00"), Decimal("0.0199"), 30) == plan.installment_amount


def test_far_above_the_limit_is_high_risk(submission):
    a = policy.assess(submission(customer={"monthlyRevenue": "2000.00"}))
    assert codes(a)["HIGH_INSTALLMENT_TO_INCOME"] is RiskLevel.HIGH
    assert a.recommendation is Recommendation.UNFAVORABLE


def test_no_plan_when_even_the_longest_term_does_not_fit(submission):
    a = policy.assess(submission(amount="500000.00", customer={"monthlyRevenue": "2000.00"}))
    assert a.suggested_plan is None
    assert "LARGE_AMOUNT" in codes(a)


def test_companies_have_a_stricter_limit_on_revenue(submission):
    # 1,056.22 / 4,500 = 23.5%: fine for a person (30%), above the 20% limit for a company
    pf = policy.assess(submission(customer={"monthlyRevenue": "4500.00"}))
    pj = policy.assess(submission(customer={"type": "PJ", "monthlyRevenue": "4500.00"},
                                  documents=["FINANCIAL_STATEMENT"]))
    assert "HIGH_INSTALLMENT_TO_INCOME" not in codes(pf)
    assert codes(pj)["HIGH_INSTALLMENT_TO_INCOME"] is RiskLevel.MEDIUM


def test_documents_and_history_flags(submission):
    a = policy.assess(submission(kind="RENEGOTIATION", documents=[],
                                 customer={"rejectedLast12Months": 1, "otherOpenProposals": 2,
                                           "customerSince": "2026-09-01"}))
    assert codes(a) == {
        "MISSING_INCOME_PROOF": RiskLevel.MEDIUM,
        "MISSING_DEBT_STATEMENT": RiskLevel.LOW,
        "RECENT_REJECTIONS": RiskLevel.MEDIUM,
        "CONCURRENT_PROPOSALS": RiskLevel.LOW,
        "NEW_CUSTOMER": RiskLevel.LOW,
    }
    assert a.risk_level is RiskLevel.HIGH  # two MEDIUM flags add up to HIGH


def test_one_medium_flag_is_caution(submission):
    a = policy.assess(submission(documents=[]))
    assert a.risk_level is RiskLevel.MEDIUM
    assert a.recommendation is Recommendation.CAUTION
