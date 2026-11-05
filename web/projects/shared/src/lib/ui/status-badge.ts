import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import type { ProposalStatus } from '../api/api';
import { I18n, Text } from '../i18n';

export const STATUS_LABEL: Record<ProposalStatus, Text> = {
  DRAFT: { en: 'Draft', pt: 'Rascunho' },
  SUBMITTED: { en: 'Submitted', pt: 'Enviada' },
  IN_ANALYSIS: { en: 'In analysis', pt: 'Em análise' },
  APPROVED: { en: 'Approved', pt: 'Aprovada' },
  REJECTED: { en: 'Rejected', pt: 'Reprovada' },
  CANCELLED: { en: 'Cancelled', pt: 'Cancelada' },
};

@Component({
  selector: 'cd-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [attr.data-status]="status()">{{ label() }}</span>`,
  styles: `
    .badge {
      display: inline-block;
      font: 700 11px/1 var(--mono);
      letter-spacing: 0.08em;
      text-transform: uppercase;
      padding: 6px 8px;
      border-radius: 999px;
      border: 1px solid currentColor;
      white-space: nowrap;
      color: var(--muted);
    }
    [data-status='SUBMITTED'] {
      color: var(--sky);
    }
    [data-status='IN_ANALYSIS'] {
      color: var(--yellow);
    }
    [data-status='APPROVED'] {
      color: var(--green);
    }
    [data-status='REJECTED'] {
      color: var(--red);
    }
    [data-status='CANCELLED'] {
      color: var(--faint);
    }
  `,
})
export class StatusBadge {
  private readonly i18n = inject(I18n);
  readonly status = input.required<ProposalStatus>();
  protected readonly label = computed(() => this.i18n.t(STATUS_LABEL[this.status()]));
}
