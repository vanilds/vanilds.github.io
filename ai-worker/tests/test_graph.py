import json

from creditdesk_worker.graph import build_graph, draft_opinion
from creditdesk_worker.messages import Recommendation
from creditdesk_worker.writer import TEMPLATE_MODEL

GOOD = json.dumps({"en": "Opinion: favorable. The installment takes 13% of income, within the 30% limit.",
                   "pt": "Parecer: favorável. A parcela compromete 13% da renda, dentro do limite de 30%."})
BAD = json.dumps({"en": "The proposal is approved.", "pt": "A proposta foi aprovada."})


class FakeWriter:
    model = "fake-llm"

    def __init__(self, *answers):
        self.answers = list(answers)
        self.calls = []

    async def draft(self, facts, feedback):
        self.calls.append(list(feedback))
        answer = self.answers.pop(0)
        if isinstance(answer, Exception):
            raise answer
        return answer


async def test_without_a_model_the_template_writes_the_summary(submission):
    opinion = await draft_opinion(build_graph(None), submission())
    assert opinion.model == TEMPLATE_MODEL
    assert opinion.recommendation is Recommendation.FAVORABLE
    assert opinion.summary.pt.startswith("Parecer: favorável")


async def test_a_good_draft_is_accepted(submission):
    writer = FakeWriter(GOOD)
    opinion = await draft_opinion(build_graph(writer), submission())
    assert opinion.model == "fake-llm"
    assert opinion.summary.en.startswith("Opinion: favorable.")
    assert writer.calls == [[]]


async def test_a_refused_draft_is_written_again_with_the_reasons(submission):
    writer = FakeWriter(BAD, GOOD)
    opinion = await draft_opinion(build_graph(writer), submission())
    assert opinion.model == "fake-llm"
    assert len(writer.calls) == 2
    assert any("states a decision" in reason for reason in writer.calls[1])


async def test_two_refusals_fall_back_to_the_template(submission):
    writer = FakeWriter(BAD, BAD)
    opinion = await draft_opinion(build_graph(writer), submission())
    assert opinion.model == TEMPLATE_MODEL
    assert len(writer.calls) == 2  # bounded: never a third call


async def test_a_model_error_falls_back_to_the_template_at_once(submission):
    writer = FakeWriter(TimeoutError("provider timed out"))
    opinion = await draft_opinion(build_graph(writer), submission())
    assert opinion.model == TEMPLATE_MODEL
    assert len(writer.calls) == 1


async def test_the_model_cannot_change_the_recommendation(submission):
    # Rules say UNFAVORABLE; a draft calling it favorable is refused, twice, so the template is used
    favorable = json.dumps({"en": "Opinion: favorable, the installment takes 53% of income.",
                            "pt": "Parecer: favorável, a parcela compromete 53% da renda."})
    writer = FakeWriter(favorable, favorable)
    opinion = await draft_opinion(build_graph(writer), submission(customer={"monthlyRevenue": "2000.00"}))
    assert opinion.recommendation is Recommendation.UNFAVORABLE
    assert opinion.model == TEMPLATE_MODEL


async def test_opinion_carries_the_submission_version_and_camel_case_json(submission):
    proposal = submission(proposalVersion=7)
    opinion = await draft_opinion(build_graph(None), proposal)
    payload = json.loads(opinion.to_json())
    assert payload["proposalId"] == str(proposal.proposal_id)
    assert payload["proposalVersion"] == 7
    assert set(payload) >= {"recommendation", "riskLevel", "summary", "riskFlags", "indicators", "suggestedPlan",
                            "model", "generatedAt"}
