"""The opinion as a LangGraph state machine.

    assess ─▶ facts ─┬─ model configured ─▶ write ─┬─ passes the guardrails ─▶ accept ─▶ END
                     │                       ▲      ├─ refused, first time ─┘ (again, with the reasons)
                     │                              └─ refused twice, or model error ─▶ template ─▶ END
                     └─ no model ──────────────────────────────────────────────────▶ template ─▶ END

Why a graph and not one function: each step is a node with typed state, so the agent loop (write → check → write
again with the reasons) is explicit, testable node by node, and bounded — at most MAX_DRAFTS calls to the model,
then the deterministic template. The model never decides: `assess` (pure rules) already did.
"""

import logging
from datetime import UTC, datetime
from typing import Literal, TypedDict

from langgraph.graph import END, START, StateGraph

from . import policy
from .messages import OpinionDrafted, ProposalSubmitted, Summary
from .writer import TEMPLATE_MODEL, Facts, Writer, check_summary, template_summary

log = logging.getLogger(__name__)

MAX_DRAFTS = 2


class OpinionState(TypedDict, total=False):
    proposal: ProposalSubmitted
    assessment: policy.Assessment
    facts: Facts
    drafts: int
    feedback: list[str]
    summary: Summary
    model: str


def build_graph(writer: Writer | None):
    async def assess(state: OpinionState) -> OpinionState:
        """Indicators, risk flags, risk level, recommendation and a plan that fits: rules only."""
        return {"assessment": policy.assess(state["proposal"])}

    async def facts(state: OpinionState) -> OpinionState:
        """What the text may say: the assessment without anything else from the message."""
        p, a = state["proposal"], state["assessment"]
        known = Facts(customer_type=p.customer.type, kind=p.kind.value, installments=p.installments,
                      installment_to_income=float(a.indicators["installmentToIncome"]),
                      installment_limit=float(a.indicators["installmentLimit"]), risk_level=a.risk_level,
                      recommendation=a.recommendation, flags=a.flags, suggested_plan=a.suggested_plan)
        return {"facts": known, "drafts": 0, "feedback": []}

    async def write(state: OpinionState) -> OpinionState:
        drafts = state["drafts"] + 1
        try:
            raw = await writer.draft(state["facts"], state["feedback"])
        except Exception as error:  # noqa: BLE001 — any provider failure means: use the template
            log.warning("Model call failed (%s: %s); falling back to the template", type(error).__name__, error)
            return {"drafts": MAX_DRAFTS, "feedback": ["model unavailable"], "summary": None}
        summary, problems = check_summary(raw, state["facts"])
        if problems:
            log.info("Draft %d refused by the guardrails: %s", drafts, " ".join(problems))
        return {"drafts": drafts, "feedback": problems, "summary": summary}

    def after_write(state: OpinionState) -> Literal["accept", "write", "template"]:
        if state.get("summary") is not None:
            return "accept"
        return "write" if state["drafts"] < MAX_DRAFTS else "template"

    async def accept(state: OpinionState) -> OpinionState:
        return {"model": writer.model}

    async def template(state: OpinionState) -> OpinionState:
        return {"summary": template_summary(state["facts"]), "model": TEMPLATE_MODEL}

    graph = StateGraph(OpinionState)
    graph.add_node("assess", assess)
    graph.add_node("facts", facts)
    graph.add_node("write", write)
    graph.add_node("accept", accept)
    graph.add_node("template", template)
    graph.add_edge(START, "assess")
    graph.add_edge("assess", "facts")
    graph.add_conditional_edges("facts", lambda _: "write" if writer else "template",
                                {"write": "write", "template": "template"})
    graph.add_conditional_edges("write", after_write, {"accept": "accept", "write": "write", "template": "template"})
    graph.add_edge("accept", END)
    graph.add_edge("template", END)
    return graph.compile()


async def draft_opinion(graph, proposal: ProposalSubmitted) -> OpinionDrafted:
    state = await graph.ainvoke({"proposal": proposal})
    a: policy.Assessment = state["assessment"]
    return OpinionDrafted(
        proposal_id=proposal.proposal_id,
        proposal_version=proposal.proposal_version,
        recommendation=a.recommendation,
        risk_level=a.risk_level,
        summary=state["summary"],
        risk_flags=a.flags,
        indicators=a.indicators,
        suggested_plan=a.suggested_plan,
        model=state["model"],
        generated_at=datetime.now(UTC),
    )
