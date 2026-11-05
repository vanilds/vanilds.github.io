import type { Proposal, ProposalAction, ProposalStatus } from './api/api';
import type { SessionUser } from './auth';

/**
 * Same state machine as api/…/ProposalAction.java, used only to decide which buttons to show.
 * The API stays the authority: if this table drifts, the user sees a 403/422 problem, never a wrong state.
 */
const MOVES: Record<ProposalAction, { from: ProposalStatus; by: 'owner' | 'decider' }> = {
  submit: { from: 'DRAFT', by: 'owner' },
  cancel: { from: 'DRAFT', by: 'owner' },
  'start-analysis': { from: 'SUBMITTED', by: 'decider' },
  approve: { from: 'IN_ANALYSIS', by: 'decider' },
  reject: { from: 'IN_ANALYSIS', by: 'decider' },
  return: { from: 'IN_ANALYSIS', by: 'decider' },
};

export function availableActions(
  proposal: Pick<Proposal, 'status' | 'analystId'>,
  user: SessionUser | null,
): ProposalAction[] {
  if (!user) return [];
  const isOwner = proposal.analystId === user.id;
  const isManager = user.roles.includes('MANAGER');
  const isAnalyst = isManager || user.roles.includes('ANALYST');
  return (Object.keys(MOVES) as ProposalAction[]).filter((action) => {
    const move = MOVES[action];
    if (move.from !== proposal.status) return false;
    // owner moves: the author (analyst or manager); decider moves: a manager who is NOT the author (four-eyes)
    return move.by === 'owner' ? isAnalyst && isOwner : isManager && !isOwner;
  });
}

/** Only drafts can be edited, and only by their author. */
export function canEdit(
  proposal: Pick<Proposal, 'status' | 'analystId'>,
  user: SessionUser | null,
): boolean {
  return !!user && proposal.status === 'DRAFT' && proposal.analystId === user.id;
}
