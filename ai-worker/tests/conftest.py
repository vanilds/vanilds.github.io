import uuid
from datetime import UTC, date, datetime
from decimal import Decimal

import pytest

from creditdesk_worker.messages import ProposalSubmitted
from creditdesk_worker.policy import installment


def make_submission(**overrides) -> ProposalSubmitted:
    """A healthy PF proposal: R$ 20,000 in 24× at 1.99%/month, income R$ 8,000, proof of income attached."""
    amount = Decimal(overrides.pop("amount", "20000.00"))
    rate = Decimal(overrides.pop("monthly_rate", "0.0199"))
    n = overrides.pop("installments", 24)
    customer = {
        "type": "PF", "monthlyRevenue": "8000.00", "customerSince": "2023-01-10",
        "approvedLast12Months": 1, "rejectedLast12Months": 0, "otherOpenProposals": 0,
    } | overrides.pop("customer", {})
    data = {
        "proposalId": str(uuid.uuid4()), "proposalVersion": 1, "kind": "NEW_CREDIT",
        "amount": str(amount), "installments": n, "monthlyRate": str(rate),
        "installmentAmount": str(installment(amount, rate, n)),
        "customer": customer, "documents": ["INCOME_PROOF"],
        "submittedAt": datetime(2026, 11, 5, 12, 0, tzinfo=UTC).isoformat(),
    } | overrides
    return ProposalSubmitted.model_validate(data)


@pytest.fixture
def submission():
    return make_submission


@pytest.fixture
def today() -> date:
    return date(2026, 11, 5)
