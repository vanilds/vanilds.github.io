"""Credit policy: deterministic rules, no AI.

The rules decide the risk level and the recommendation; the language model (if any) only writes the summary.
That split is deliberate: a regulator, an auditor or the analyst can read these thresholds and get the same answer
every time, and a model hallucination can never turn an UNFAVORABLE case into a FAVORABLE one.

Thresholds are illustrative for the demo, not any bank's real policy.
"""

from dataclasses import dataclass
from datetime import date
from decimal import ROUND_HALF_UP, Decimal

from .messages import (
    CustomerType,
    ProposalKind,
    ProposalSubmitted,
    Recommendation,
    RiskFlag,
    RiskLevel,
    SuggestedPlan,
)

CENTS = Decimal("0.01")

# Share of monthly income (PF) or revenue (PJ) the installment may take
INSTALLMENT_LIMIT = {CustomerType.PF: Decimal("0.30"), CustomerType.PJ: Decimal("0.20")}
# Above limit + this margin, the flag is HIGH instead of MEDIUM
HIGH_MARGIN = Decimal("0.10")
# Terms tried when the requested installment does not fit; never beyond 120 (the API's maximum)
CANDIDATE_TERMS = (12, 18, 24, 30, 36, 48, 60, 72, 84, 96, 120)
INCOME_PROOFS = {
    CustomerType.PF: {"INCOME_PROOF", "BANK_STATEMENT"},
    CustomerType.PJ: {"FINANCIAL_STATEMENT", "BANK_STATEMENT"},
}


def installment(amount: Decimal, monthly_rate: Decimal, n: int) -> Decimal:
    """Tabela Price, same formula and rounding as PriceTable.java."""
    if monthly_rate == 0:
        return (amount / n).quantize(CENTS, ROUND_HALF_UP)
    factor = monthly_rate / (1 - (1 + monthly_rate) ** -n)
    return (amount * factor).quantize(CENTS, ROUND_HALF_UP)


@dataclass(frozen=True)
class Assessment:
    indicators: dict[str, float | int]
    flags: list[RiskFlag]
    risk_level: RiskLevel
    recommendation: Recommendation
    suggested_plan: SuggestedPlan | None


def months_between(start: date, end: date) -> int:
    return max(0, (end.year - start.year) * 12 + end.month - start.month - (1 if end.day < start.day else 0))


def indicators(p: ProposalSubmitted) -> dict[str, float | int]:
    income = p.customer.monthly_revenue
    total = p.installment_amount * p.installments
    return {
        "installmentToIncome": round(float(p.installment_amount / income), 4),
        "installmentLimit": float(INSTALLMENT_LIMIT[p.customer.type]),
        "amountToMonthlyIncome": round(float(p.amount / income), 2),
        "totalPayable": float(total.quantize(CENTS)),
        "totalInterest": float((total - p.amount).quantize(CENTS)),
        "annualRate": round(float((1 + p.monthly_rate) ** 12 - 1), 4),
        "relationshipMonths": months_between(p.customer.customer_since, p.submitted_at.date()),
    }


def flags(p: ProposalSubmitted, ind: dict[str, float | int]) -> list[RiskFlag]:
    found: list[RiskFlag] = []
    limit = INSTALLMENT_LIMIT[p.customer.type]
    ratio = Decimal(str(ind["installmentToIncome"]))
    who = "income" if p.customer.type is CustomerType.PF else "revenue"
    if ratio > limit:
        severity = RiskLevel.HIGH if ratio > limit + HIGH_MARGIN else RiskLevel.MEDIUM
        found.append(RiskFlag(code="HIGH_INSTALLMENT_TO_INCOME", severity=severity,
                              message=f"Installment takes {ratio:.0%} of monthly {who}; the limit is {limit:.0%}."))
    if p.customer.type is CustomerType.PF and ind["amountToMonthlyIncome"] > 12:
        found.append(RiskFlag(code="LARGE_AMOUNT", severity=RiskLevel.MEDIUM,
                              message=f"Amount is {ind['amountToMonthlyIncome']:.1f}× the monthly income."))
    if not INCOME_PROOFS[p.customer.type] & set(p.documents):
        found.append(RiskFlag(code="MISSING_INCOME_PROOF", severity=RiskLevel.MEDIUM,
                              message=f"No proof of {who} attached."))
    if p.kind is ProposalKind.RENEGOTIATION and "DEBT_STATEMENT" not in p.documents:
        found.append(RiskFlag(code="MISSING_DEBT_STATEMENT", severity=RiskLevel.LOW,
                              message="Renegotiation without a statement of the current debt."))
    rejected = p.customer.rejected_last12_months
    if rejected:
        found.append(RiskFlag(code="RECENT_REJECTIONS", severity=RiskLevel.HIGH if rejected >= 2 else RiskLevel.MEDIUM,
                              message=f"{rejected} proposal(s) rejected in the last 12 months."))
    if p.customer.other_open_proposals:
        found.append(RiskFlag(code="CONCURRENT_PROPOSALS", severity=RiskLevel.LOW,
                              message=f"{p.customer.other_open_proposals} other proposal(s) in progress."))
    if ind["relationshipMonths"] < 6:
        found.append(RiskFlag(code="NEW_CUSTOMER", severity=RiskLevel.LOW,
                              message="Customer for less than 6 months."))
    return found


def risk_level(found: list[RiskFlag]) -> RiskLevel:
    severities = [f.severity for f in found]
    if RiskLevel.HIGH in severities or severities.count(RiskLevel.MEDIUM) >= 2:
        return RiskLevel.HIGH
    if RiskLevel.MEDIUM in severities:
        return RiskLevel.MEDIUM
    return RiskLevel.LOW


RECOMMENDATION = {
    RiskLevel.LOW: Recommendation.FAVORABLE,
    RiskLevel.MEDIUM: Recommendation.CAUTION,
    RiskLevel.HIGH: Recommendation.UNFAVORABLE,
}


def suggested_plan(p: ProposalSubmitted) -> SuggestedPlan | None:
    """The shortest longer term whose installment fits the limit, at the same rate. None if none fits."""
    income = p.customer.monthly_revenue
    limit = INSTALLMENT_LIMIT[p.customer.type]
    if p.installment_amount / income <= limit:
        return None
    for n in CANDIDATE_TERMS:
        if n <= p.installments:
            continue
        value = installment(p.amount, p.monthly_rate, n)
        ratio = value / income
        if ratio <= limit:
            return SuggestedPlan(installments=n, installment_amount=value,
                                 installment_to_income=ratio.quantize(Decimal("0.0001"), ROUND_HALF_UP))
    return None


def assess(p: ProposalSubmitted) -> Assessment:
    ind = indicators(p)
    found = flags(p, ind)
    level = risk_level(found)
    return Assessment(indicators=ind, flags=found, risk_level=level, recommendation=RECOMMENDATION[level],
                      suggested_plan=suggested_plan(p))
