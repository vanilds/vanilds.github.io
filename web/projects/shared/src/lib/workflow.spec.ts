import { availableActions, canEdit } from './workflow';
import type { SessionUser } from './auth';

const ana: SessionUser = { id: 'ana', username: 'ana', name: 'Ana', roles: ['ANALYST'] };
const marcos: SessionUser = {
  id: 'marcos',
  username: 'marcos',
  name: 'Marcos',
  roles: ['MANAGER'],
};
const aline: SessionUser = { id: 'aline', username: 'aline', name: 'Aline', roles: ['AUDITOR'] };

describe('workflow (mirror of ProposalAction.java)', () => {
  it('lets the author submit or cancel a draft', () => {
    expect(availableActions({ status: 'DRAFT', analystId: 'ana' }, ana)).toEqual([
      'submit',
      'cancel',
    ]);
  });

  it('gives nothing to another analyst', () => {
    expect(availableActions({ status: 'DRAFT', analystId: 'bruno' }, ana)).toEqual([]);
  });

  it('lets a manager decide on someone else’s proposal', () => {
    expect(availableActions({ status: 'SUBMITTED', analystId: 'ana' }, marcos)).toEqual([
      'start-analysis',
    ]);
    expect(availableActions({ status: 'IN_ANALYSIS', analystId: 'ana' }, marcos)).toEqual([
      'approve',
      'reject',
      'return',
    ]);
  });

  it('applies four-eyes: a manager cannot decide on their own proposal', () => {
    expect(availableActions({ status: 'IN_ANALYSIS', analystId: 'marcos' }, marcos)).toEqual([]);
  });

  it('keeps the auditor read-only', () => {
    expect(availableActions({ status: 'IN_ANALYSIS', analystId: 'ana' }, aline)).toEqual([]);
    expect(availableActions({ status: 'SUBMITTED', analystId: 'ana' }, aline)).toEqual([]);
  });

  it('allows editing drafts by their author only', () => {
    expect(canEdit({ status: 'DRAFT', analystId: 'ana' }, ana)).toBe(true);
    expect(canEdit({ status: 'SUBMITTED', analystId: 'ana' }, ana)).toBe(false);
    expect(canEdit({ status: 'DRAFT', analystId: 'ana' }, marcos)).toBe(false);
  });
});
