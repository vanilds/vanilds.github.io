import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  I18n,
  Problem,
  ProblemBanner,
  Proposal,
  ProposalAction,
  ProposalsApi,
  REASON_REQUIRED,
  STATUS_LABEL,
  Session,
  StatusBadge,
  StatusChange,
  Text,
  Versioned,
  availableActions,
  canEdit,
  toProblem,
} from '@creditdesk/shared';
import { ProposalForm } from '../form/proposal-form';
import { AiOpinion } from './ai-opinion';
import { ProposalDocuments } from './proposal-documents';

const ACTION_LABEL: Record<ProposalAction, Text> = {
  submit: { en: 'Submit for analysis', pt: 'Enviar para análise' },
  cancel: { en: 'Cancel proposal', pt: 'Cancelar proposta' },
  'start-analysis': { en: 'Start analysis', pt: 'Iniciar análise' },
  approve: { en: 'Approve', pt: 'Aprovar' },
  reject: { en: 'Reject', pt: 'Reprovar' },
  return: { en: 'Return to analyst', pt: 'Devolver ao analista' },
};

@Component({
  selector: 'cd-proposal-detail',
  imports: [
    RouterLink,
    FormsModule,
    ProblemBanner,
    StatusBadge,
    ProposalForm,
    AiOpinion,
    ProposalDocuments,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-head">
      <div>
        <a routerLink=".." class="back">← {{ i18n.t({ en: 'Proposals', pt: 'Propostas' }) }}</a>
        @if (record(); as r) {
          <h1>{{ r.value.customerName }}</h1>
          <p class="sub">
            <cd-status-badge [status]="r.value.status" />
            <span class="muted"
              >{{
                r.value.kind === 'NEW_CREDIT'
                  ? i18n.t({ en: 'New credit', pt: 'Crédito novo' })
                  : i18n.t({ en: 'Renegotiation', pt: 'Renegociação' })
              }}
              · {{ i18n.t({ en: 'analyst', pt: 'analista' }) }} {{ r.value.analystName }}</span
            >
          </p>
        } @else {
          <h1>…</h1>
        }
      </div>
      @if (record(); as r) {
        <a class="btn" [routerLink]="['../../customers', r.value.customerId]">{{
          i18n.t({ en: 'Customer', pt: 'Cliente' })
        }}</a>
      }
    </header>

    <cd-problem-banner [problem]="problem()" (reload)="load()" />

    @if (record(); as r) {
      @if (editing()) {
        <cd-proposal-form
          [existing]="r"
          (saved)="afterChange($event)"
          (cancelled)="editing.set(false)"
          (reloadRequested)="load()"
        />
      } @else {
        <section class="card">
          <dl class="facts">
            <div>
              <dt>{{ i18n.t({ en: 'Amount', pt: 'Valor' }) }}</dt>
              <dd>{{ i18n.money(r.value.amount) }}</dd>
            </div>
            <div>
              <dt>{{ i18n.t({ en: 'Installments', pt: 'Parcelas' }) }}</dt>
              <dd>{{ r.value.installments ?? '—' }}</dd>
            </div>
            <div>
              <dt>{{ i18n.t({ en: 'Monthly rate', pt: 'Taxa mensal' }) }}</dt>
              <dd>{{ i18n.percent(r.value.monthlyRate) }}</dd>
            </div>
            <div>
              <dt>{{ i18n.t({ en: 'Installment', pt: 'Parcela' }) }}</dt>
              <dd class="strong">{{ i18n.money(r.value.installmentAmount) }}</dd>
            </div>
            <div>
              <dt>{{ i18n.t({ en: 'Total to pay', pt: 'Total a pagar' }) }}</dt>
              <dd>{{ i18n.money(r.value.totalAmount) }}</dd>
            </div>
            <div>
              <dt>{{ i18n.t({ en: 'Version (ETag)', pt: 'Versão (ETag)' }) }}</dt>
              <dd class="mono">{{ r.etag }}</dd>
            </div>
          </dl>

          @if (actions().length || editable()) {
            <div class="actions">
              @if (editable()) {
                <button type="button" class="btn" (click)="editing.set(true)">
                  {{ i18n.t({ en: 'Edit draft', pt: 'Editar rascunho' }) }}
                </button>
              }
              @for (a of actions(); track a) {
                <button
                  type="button"
                  class="btn"
                  [class.btn-primary]="a === 'submit' || a === 'approve'"
                  [class.btn-danger]="a === 'reject' || a === 'cancel'"
                  [disabled]="busy()"
                  (click)="ask(a)"
                >
                  {{ i18n.t(actionLabels[a]) }}
                </button>
              }
            </div>
          } @else if (session.can('AUDITOR')) {
            <p class="muted note">
              {{
                i18n.t({
                  en: 'Read-only: auditors see everything and change nothing.',
                  pt: 'Somente leitura: auditoria vê tudo e não altera nada.',
                })
              }}
            </p>
          } @else if (
            r.value.status === 'IN_ANALYSIS' && r.value.analystId === session.user()?.id
          ) {
            <p class="muted note">
              {{
                i18n.t({
                  en: 'Four-eyes rule: another manager decides on your own proposal.',
                  pt: 'Regra dos quatro olhos: outro gestor decide sobre a sua própria proposta.',
                })
              }}
            </p>
          }
        </section>

        @if (pending(); as a) {
          <form class="card confirm" (ngSubmit)="run(r, a)">
            <p>
              <strong>{{ i18n.t(actionLabels[a]) }}</strong>
            </p>
            <label class="field">
              {{
                needsReason()
                  ? i18n.t({
                      en: 'Reason (required — goes to the history and the audit log)',
                      pt: 'Motivo (obrigatório — vai para o histórico e a auditoria)',
                    })
                  : i18n.t({ en: 'Comment (optional)', pt: 'Comentário (opcional)' })
              }}
              <textarea name="reason" rows="3" [(ngModel)]="reason"></textarea>
            </label>
            <div class="actions">
              <button type="button" class="btn btn-ghost" (click)="pending.set(null)">
                {{ i18n.t({ en: 'Back', pt: 'Voltar' }) }}
              </button>
              <button
                type="submit"
                class="btn btn-primary"
                [disabled]="busy() || (needsReason() && !reason.trim())"
              >
                {{ i18n.t({ en: 'Confirm', pt: 'Confirmar' }) }}
              </button>
            </div>
          </form>
        }
      }

      <!-- When the opinion arrives, the API has already moved the proposal: reload to show IN_ANALYSIS -->
      <cd-ai-opinion [proposal]="r.value" (arrived)="load()" />
      <cd-proposal-documents [proposal]="r.value" />

      <section class="history">
        <h2 class="kicker">{{ i18n.t({ en: 'Status history', pt: 'Histórico de status' }) }}</h2>
        <ol>
          @for (h of history(); track h.id) {
            <li>
              <span class="when">{{ i18n.dateTime(h.changedAt) }}</span>
              <span>
                {{ h.from ? i18n.t(statusLabels[h.from]) + ' → ' : ''
                }}<strong>{{ i18n.t(statusLabels[h.to]) }}</strong> · {{ h.actorName }}
                @if (h.reason) {
                  <em class="reason">“{{ h.reason }}”</em>
                }
              </span>
            </li>
          } @empty {
            <li class="muted">—</li>
          }
        </ol>
      </section>
    }
  `,
  styles: `
    .back {
      font-size: 13px;
      text-decoration: none;
    }
    .sub {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      align-items: center;
      margin: 10px 0 0;
      font-size: 14px;
    }
    .facts {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
      gap: 18px 24px;
      margin: 0;
    }
    dt {
      font: 700 11px/1 var(--mono);
      letter-spacing: 0.12em;
      text-transform: uppercase;
      color: var(--muted);
      margin-bottom: 6px;
    }
    dd {
      margin: 0;
      font-variant-numeric: tabular-nums;
    }
    dd.strong {
      font-weight: 800;
      font-size: 18px;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      justify-content: flex-end;
      margin-top: 20px;
    }
    .note {
      margin: 20px 0 0;
      font-size: 14px;
    }
    .confirm {
      margin-top: 16px;
      display: grid;
      gap: 12px;
    }
    .confirm p {
      margin: 0;
    }
    .history {
      margin-top: 28px;
    }
    .history ol {
      list-style: none;
      padding: 0;
      margin: 12px 0 0;
      border-left: 2px solid var(--line-strong);
    }
    .history li {
      padding: 0 0 14px 16px;
      position: relative;
      font-size: 14px;
      display: grid;
      gap: 2px;
    }
    .history li::before {
      content: '';
      position: absolute;
      left: -6px;
      top: 6px;
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: var(--orange);
    }
    .when {
      font: 12px var(--mono);
      color: var(--muted);
    }
    .reason {
      color: var(--muted);
    }
  `,
})
export class ProposalDetail {
  protected readonly i18n = inject(I18n);
  protected readonly session = inject(Session);
  private readonly api = inject(ProposalsApi);

  readonly id = input.required<string>();

  protected readonly actionLabels = ACTION_LABEL;
  protected readonly statusLabels = STATUS_LABEL;
  protected readonly record = signal<Versioned<Proposal> | null>(null);
  protected readonly history = signal<StatusChange[]>([]);
  protected readonly editing = signal(false);
  protected readonly busy = signal(false);
  protected readonly problem = signal<Problem | null>(null);
  protected readonly pending = signal<ProposalAction | null>(null);
  protected readonly needsReason = computed(() => {
    const a = this.pending();
    return !!a && REASON_REQUIRED.includes(a);
  });
  protected readonly actions = computed(() => {
    const r = this.record();
    return r ? availableActions(r.value, this.session.user()) : [];
  });
  protected readonly editable = computed(() => {
    const r = this.record();
    return !!r && canEdit(r.value, this.session.user());
  });
  protected reason = '';

  constructor() {
    effect(() => {
      this.id();
      untracked(() => this.load());
    });
  }

  load(): void {
    this.problem.set(null);
    this.api.get(this.id()).subscribe({
      next: (r) => {
        this.record.set(r);
        this.editing.set(false);
        this.pending.set(null);
      },
      error: (e) => this.problem.set(toProblem(e)),
    });
    this.api
      .history(this.id())
      .subscribe({ next: (h) => this.history.set(h), error: () => this.history.set([]) });
  }

  protected ask(action: ProposalAction): void {
    this.reason = '';
    // submit / start-analysis take no body; everything else asks for a reason or comment first
    if (action === 'submit' || action === 'start-analysis') {
      const r = this.record();
      if (r) this.run(r, action);
    } else {
      this.pending.set(action);
    }
  }

  protected run(r: Versioned<Proposal>, action: ProposalAction): void {
    this.busy.set(true);
    this.api.transition(r.value.id, r.etag, action, this.reason.trim()).subscribe({
      next: (updated) => this.afterChange(updated),
      error: (e) => {
        this.busy.set(false);
        this.problem.set(toProblem(e));
      },
    });
  }

  protected afterChange(updated: Versioned<Proposal>): void {
    this.busy.set(false);
    this.problem.set(null);
    this.pending.set(null);
    this.editing.set(false);
    this.record.set(updated);
    this.api.history(updated.value.id).subscribe({ next: (h) => this.history.set(h) });
  }
}
