"""Who writes the opinion's text: a fixed template, or a language model checked by guardrails.

The template is always available and is the fallback for every failure (no key configured, provider down, timeout,
an answer that fails the guardrails). The business never depends on the model being up.
"""

import json
import logging
import re
from dataclasses import dataclass
from decimal import Decimal
from typing import Protocol

import httpx

from .messages import CustomerType, Recommendation, RiskFlag, RiskLevel, SuggestedPlan, Summary

log = logging.getLogger(__name__)

TEMPLATE_MODEL = "rules-template-v1"

FLAG_TEXT_PT = {
    "HIGH_INSTALLMENT_TO_INCOME": "parcela acima do limite de comprometimento da renda",
    "LARGE_AMOUNT": "valor alto em relação à renda mensal",
    "MISSING_INCOME_PROOF": "sem comprovante de renda anexado",
    "MISSING_DEBT_STATEMENT": "renegociação sem extrato da dívida atual",
    "RECENT_REJECTIONS": "propostas reprovadas nos últimos 12 meses",
    "CONCURRENT_PROPOSALS": "outras propostas em andamento",
    "NEW_CUSTOMER": "cliente há menos de 6 meses",
}
RECOMMENDATION_EN = {
    Recommendation.FAVORABLE: "favorable",
    Recommendation.CAUTION: "favorable with caution",
    Recommendation.UNFAVORABLE: "unfavorable",
}
RECOMMENDATION_PT = {
    Recommendation.FAVORABLE: "favorável",
    Recommendation.CAUTION: "favorável com ressalvas",
    Recommendation.UNFAVORABLE: "desfavorável",
}
RISK_PT = {RiskLevel.LOW: "baixo", RiskLevel.MEDIUM: "médio", RiskLevel.HIGH: "alto"}


@dataclass(frozen=True)
class Facts:
    """Everything the text may talk about. Nothing here identifies a person."""

    customer_type: CustomerType
    kind: str
    installments: int
    installment_to_income: float
    installment_limit: float
    risk_level: RiskLevel
    recommendation: Recommendation
    flags: list[RiskFlag]
    suggested_plan: SuggestedPlan | None

    def percentages(self) -> set[int]:
        """The only percentages the text may mention (rounded), for the grounding check."""
        values = {round(self.installment_to_income * 100), round(self.installment_limit * 100)}
        if self.suggested_plan:
            values.add(round(float(self.suggested_plan.installment_to_income) * 100))
        return values

    def as_prompt_json(self) -> str:
        return json.dumps({
            "customerType": "individual" if self.customer_type is CustomerType.PF else "company",
            "kind": self.kind,
            "installments": self.installments,
            "installmentToIncomePercent": round(self.installment_to_income * 100),
            "limitPercent": round(self.installment_limit * 100),
            "riskLevel": self.risk_level.value,
            "recommendation": self.recommendation.value,
            "riskFlags": [{"code": f.code, "severity": f.severity.value, "detail": f.message} for f in self.flags],
            "suggestedPlan": None if not self.suggested_plan else {
                "installments": self.suggested_plan.installments,
                "installmentToIncomePercent": round(float(self.suggested_plan.installment_to_income) * 100),
            },
        }, ensure_ascii=False)


def _pct(value: float | Decimal) -> str:
    return f"{round(float(value) * 100)}%"


def _brl_pt(value: Decimal) -> str:
    """R$ 1.234,56 — Brazilian format for the Portuguese text."""
    return "R$ " + f"{value:,.2f}".replace(",", "_").replace(".", ",").replace("_", ".")


def _sentence_part(text: str) -> str:
    text = text.rstrip(".")
    return text[:1].lower() + text[1:]


def template_summary(f: Facts) -> Summary:
    who_en = "income" if f.customer_type is CustomerType.PF else "revenue"
    who_pt = "da renda" if f.customer_type is CustomerType.PF else "do faturamento"
    en = [f"Opinion: {RECOMMENDATION_EN[f.recommendation]} (risk {f.risk_level.value.lower()}).",
          f"The installment takes {_pct(f.installment_to_income)} of monthly {who_en} "
          f"(limit {_pct(f.installment_limit)})."]
    pt = [f"Parecer: {RECOMMENDATION_PT[f.recommendation]} (risco {RISK_PT[f.risk_level]}).",
          f"A parcela compromete {_pct(f.installment_to_income)} {who_pt} mensal "
          f"(limite {_pct(f.installment_limit)})."]
    if f.flags:
        en.append("Points of attention: " + "; ".join(_sentence_part(flag.message) for flag in f.flags) + ".")
        pt.append("Pontos de atenção: " + "; ".join(FLAG_TEXT_PT.get(flag.code, flag.code) for flag in f.flags) + ".")
    else:
        en.append("No points of attention found by the policy rules.")
        pt.append("Nenhum ponto de atenção encontrado pelas regras da política.")
    if f.suggested_plan:
        plan = f.suggested_plan
        en.append(f"A plan in {plan.installments} installments of R$ {plan.installment_amount:,.2f} would take "
                  f"{_pct(plan.installment_to_income)} and fit the limit.")
        pt.append(f"Um plano em {plan.installments} parcelas de {_brl_pt(plan.installment_amount)} comprometeria "
                  f"{_pct(plan.installment_to_income)} e caberia no limite.")
    en.append("The decision belongs to the manager.")
    pt.append("A decisão cabe ao gestor.")
    return Summary(en=" ".join(en), pt=" ".join(pt))


# ---------------------------------------------------------------------------------------------------------------
# Guardrails: the model's answer is input, so it is validated like any input before anyone reads it.
# ---------------------------------------------------------------------------------------------------------------

_PERSONAL_DATA = [
    re.compile(r"\b\d{3}\.?\d{3}\.?\d{3}-?\d{2}\b"),  # CPF
    re.compile(r"\b[0-9A-Z]{2}\.?[0-9A-Z]{3}\.?[0-9A-Z]{3}/?[0-9A-Z]{4}-?\d{2}\b"),  # CNPJ (also alphanumeric)
    re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+"),  # e-mail
]
# The opinion advises; it must never read as if the decision was already taken
_DECISION_CLAIMS = re.compile(
    r"\b(is|was|has been|are) (approved|rejected|denied)\b|\b(foi|está|esta|fica) (aprovad|reprovad|negad)",
    re.IGNORECASE)
_PERCENT = re.compile(r"(\d+(?:[.,]\d+)?)\s?%")
# (required, forbidden) words per recommendation, English and Portuguese. Lookbehinds: "unfavorable" contains
# "favorable", "desfavorável" contains "favorável".
_RECOMMENDATION_WORDS = {
    Recommendation.FAVORABLE: ((r"(?<!un)favorable", r"(?<!des)favorável"), (r"unfavorable", r"desfavorável")),
    Recommendation.CAUTION: ((r"caution", r"ressalva"), (r"unfavorable", r"desfavorável")),
    Recommendation.UNFAVORABLE: ((r"unfavorable", r"desfavorável"), ("", "")),
}


def check_summary(raw: str, facts: Facts) -> tuple[Summary | None, list[str]]:
    """Returns the summary if it passes every check, otherwise None and the reasons (fed back to the model)."""
    try:
        data = json.loads(raw)
        summary = Summary(en=str(data["en"]).strip(), pt=str(data["pt"]).strip())
    except (ValueError, KeyError, TypeError):
        return None, ['Answer must be a JSON object with exactly the keys "en" and "pt".']
    problems = []
    for lang, text in (("en", summary.en), ("pt", summary.pt)):
        if not 40 <= len(text) <= 1200:
            problems.append(f'"{lang}" must have between 40 and 1200 characters.')
        if any(p.search(text) for p in _PERSONAL_DATA):
            problems.append(f'"{lang}" contains something that looks like personal data; remove it.')
        if _DECISION_CLAIMS.search(text):
            problems.append(f'"{lang}" states a decision; the opinion only advises, the manager decides.')
        allowed = facts.percentages()
        invented = {float(m.replace(",", ".")) for m in _PERCENT.findall(text)} - {float(v) for v in allowed}
        if invented:
            problems.append(f'"{lang}" mentions percentages that are not in the facts: '
                            + ", ".join(f"{v:g}%" for v in sorted(invented)) + ".")
    required, forbidden = _RECOMMENDATION_WORDS[facts.recommendation]
    for lang, text, pattern in (("en", summary.en, required[0]), ("pt", summary.pt, required[1])):
        if not re.search(pattern, text, re.IGNORECASE):
            problems.append(f'"{lang}" must state the recommendation given in the facts.')
    for lang, text, pattern in (("en", summary.en, forbidden[0]), ("pt", summary.pt, forbidden[1])):
        if pattern and re.search(pattern, text, re.IGNORECASE):
            problems.append(f'"{lang}" contradicts the recommendation given in the facts.')
    return (None, problems) if problems else (summary, [])


# ---------------------------------------------------------------------------------------------------------------
# Language model: any OpenAI-compatible chat completions endpoint (a hosted free tier, or a local Ollama).
# ---------------------------------------------------------------------------------------------------------------

SYSTEM_PROMPT = """You write the summary of a credit opinion for a bank analyst in Brazil.
You receive the facts as JSON. The risk level and the recommendation were decided by policy rules: report them,
never change them. Use only the facts given; do not invent numbers, names or documents.
The opinion is advisory: never say the proposal is approved or rejected; the manager decides.
Answer with a JSON object only: {"en": "<2 to 4 sentences in English>", "pt": "<the same in Brazilian Portuguese>"}."""


class Writer(Protocol):
    model: str

    async def draft(self, facts: Facts, feedback: list[str]) -> str: ...


class LlmWriter:
    def __init__(self, base_url: str, api_key: str, model: str, timeout_seconds: float):
        self.model = model
        self._client = httpx.AsyncClient(base_url=base_url.rstrip("/"), timeout=timeout_seconds,
                                         headers={"Authorization": f"Bearer {api_key}"})

    async def draft(self, facts: Facts, feedback: list[str]) -> str:
        messages = [{"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": facts.as_prompt_json()}]
        if feedback:
            messages.append({"role": "user", "content": "Your previous answer was refused: " + " ".join(feedback)
                             + " Write it again following every rule."})
        response = await self._client.post("/chat/completions", json={
            "model": self.model,
            "messages": messages,
            "temperature": 0.2,
            "response_format": {"type": "json_object"},
        })
        response.raise_for_status()
        return response.json()["choices"][0]["message"]["content"]

    async def close(self) -> None:
        await self._client.aclose()
