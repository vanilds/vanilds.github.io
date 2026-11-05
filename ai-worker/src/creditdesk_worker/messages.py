"""Message contracts with the API.

Mirrors api/src/main/java/.../proposal/ProposalSubmitted.java (input) and
.../proposal/opinion/OpinionDrafted.java (output). Contracts only grow: unknown fields are ignored, so the API can
add fields without breaking a worker that has not been redeployed yet.
"""

from datetime import date, datetime
from decimal import Decimal
from enum import StrEnum
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class _Message(BaseModel):
    # JSON in camelCase (like the API), Python attributes in snake_case
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="ignore", frozen=True)


class CustomerType(StrEnum):
    PF = "PF"  # individual
    PJ = "PJ"  # company


class ProposalKind(StrEnum):
    NEW_CREDIT = "NEW_CREDIT"
    RENEGOTIATION = "RENEGOTIATION"


class CustomerProfile(_Message):
    """No name, no CPF/CNPJ, no e-mail: the API never sends anything that identifies the customer."""

    type: CustomerType
    monthly_revenue: Decimal = Field(gt=0)
    customer_since: date
    approved_last12_months: int = Field(ge=0)
    rejected_last12_months: int = Field(ge=0)
    other_open_proposals: int = Field(ge=0)


class ProposalSubmitted(_Message):
    """Routing key proposal.submitted."""

    proposal_id: UUID
    proposal_version: int = Field(ge=0)
    kind: ProposalKind
    amount: Decimal = Field(gt=0)
    installments: int = Field(ge=1, le=120)
    monthly_rate: Decimal = Field(ge=0, lt=1)
    installment_amount: Decimal = Field(gt=0)
    customer: CustomerProfile
    documents: list[str] = Field(default_factory=list)
    submitted_at: datetime


class Recommendation(StrEnum):
    FAVORABLE = "FAVORABLE"
    CAUTION = "CAUTION"
    UNFAVORABLE = "UNFAVORABLE"


class RiskLevel(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class RiskFlag(_Message):
    code: str
    severity: RiskLevel
    message: str


class SuggestedPlan(_Message):
    installments: int
    installment_amount: Decimal
    installment_to_income: Decimal


class Summary(_Message):
    en: str
    pt: str


class OpinionDrafted(_Message):
    """Routing key opinion.drafted. The AMQP message_id is the opinion id (see consumer.opinion_id)."""

    proposal_id: UUID
    proposal_version: int
    recommendation: Recommendation
    risk_level: RiskLevel
    summary: Summary
    risk_flags: list[RiskFlag]
    indicators: dict[str, float | int]
    suggested_plan: SuggestedPlan | None
    model: str
    generated_at: datetime

    def to_json(self) -> bytes:
        return self.model_dump_json(by_alias=True).encode()
