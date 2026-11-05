import json

import pytest

from creditdesk_worker import policy
from creditdesk_worker.messages import Recommendation
from creditdesk_worker.writer import Facts, check_summary, template_summary


def facts_for(proposal) -> Facts:
    a = policy.assess(proposal)
    return Facts(customer_type=proposal.customer.type, kind=proposal.kind.value, installments=proposal.installments,
                 installment_to_income=a.indicators["installmentToIncome"],
                 installment_limit=a.indicators["installmentLimit"], risk_level=a.risk_level,
                 recommendation=a.recommendation, flags=a.flags, suggested_plan=a.suggested_plan)


@pytest.mark.parametrize("overrides", [
    {},
    {"customer": {"monthlyRevenue": "3000.00"}},
    {"customer": {"monthlyRevenue": "2000.00", "rejectedLast12Months": 2}, "documents": []},
    {"customer": {"type": "PJ", "monthlyRevenue": "4500.00"}, "kind": "RENEGOTIATION"},
])
def test_the_template_always_passes_its_own_guardrails(submission, overrides):
    facts = facts_for(submission(**overrides))
    summary = template_summary(facts)
    accepted, problems = check_summary(json.dumps({"en": summary.en, "pt": summary.pt}), facts)
    assert problems == []
    assert accepted == summary


def test_template_text(submission):
    facts = facts_for(submission(customer={"monthlyRevenue": "3000.00"}))
    summary = template_summary(facts)
    assert summary.pt.startswith("Parecer: favorável com ressalvas (risco médio).")
    assert "35%" in summary.pt and "30 parcelas de R$ 891,77" in summary.pt
    assert "30 installments of R$ 891.77" in summary.en
    assert "Points of attention: installment takes 35%" in summary.en
    assert summary.en.endswith("The decision belongs to the manager.")


GOOD = {"en": "Opinion: favorable with caution. The installment takes 35% of income, above the 30% limit.",
        "pt": "Parecer: favorável com ressalvas. A parcela compromete 35% da renda, acima do limite de 30%."}


@pytest.mark.parametrize(("change", "problem"), [
    ({"en": "Opinion: favorable with caution, customer 123.456.789-09 has 35% of income committed."}, "personal data"),
    ({"pt": "Parecer: favorável com ressalvas; contato ana@example.com, 35% da renda comprometida."}, "personal data"),
    ({"en": "Favorable with caution: the proposal is approved, 35% of income committed."}, "states a decision"),
    ({"pt": "Parecer favorável com ressalvas: a proposta foi aprovada, parcela de 35% da renda."}, "states a decision"),
    ({"en": "Opinion: favorable with caution. The installment takes 42% of income, which is high."}, "42%"),
    ({"en": "Opinion: unfavorable. The installment takes 35% of income, above the 30% limit."}, "contradicts"),
    ({"en": "Too short."}, "between 40 and 1200"),
])
def test_guardrails_refuse(submission, change, problem):
    facts = facts_for(submission(customer={"monthlyRevenue": "3000.00"}))
    assert facts.recommendation is Recommendation.CAUTION
    accepted, problems = check_summary(json.dumps(GOOD | change), facts)
    assert accepted is None
    assert any(problem in p for p in problems), problems


def test_guardrails_accept_a_good_answer_and_refuse_non_json(submission):
    facts = facts_for(submission(customer={"monthlyRevenue": "3000.00"}))
    assert check_summary(json.dumps(GOOD), facts)[1] == []
    assert check_summary("Sure! Here is the opinion: ...", facts)[0] is None
