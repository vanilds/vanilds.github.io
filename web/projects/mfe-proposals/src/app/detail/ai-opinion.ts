import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { I18n, Opinion, Proposal, ProposalsApi } from '@creditdesk/shared';
import { FLAG_LABEL, RECOMMENDATION_LABEL, RISK_LABEL } from './labels';

/** Every 3 s for up to a minute after a submit; the worker normally answers in under a second. */
const POLL_MS = 3000;
const MAX_POLLS = 20;

/**
 * The AI credit opinion. The submit returns at once (the worker runs asynchronously through RabbitMQ), so while
 * the proposal is SUBMITTED this panel polls until the opinion arrives, then tells the page to reload — by then
 * the API has moved the proposal to IN_ANALYSIS. Polling is enough here: one screen, one short wait. Many
 * screens or a long wait would justify Server-Sent Events.
 */
@Component({
  selector: 'cd-ai-opinion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="opinion" [attr.data-recommendation]="shown()?.recommendation">
      <h2 class="kicker">
        {{ i18n.t({ en: 'AI credit opinion', pt: 'Parecer de crédito (IA)' }) }}
      </h2>

      @if (shown(); as o) {
        <div class="card">
          <p class="verdict">
            <strong>{{ i18n.t(recommendations[o.recommendation]) }}</strong>
            <span class="muted">· {{ i18n.t(risks[o.riskLevel]) }}</span>
            @if (!o.current) {
              <span class="tag">{{
                i18n.t({ en: 'previous submission', pt: 'envio anterior' })
              }}</span>
            }
          </p>
          <p class="summary">{{ i18n.lang() === 'pt' ? o.summary.pt : o.summary.en }}</p>

          @if (o.riskFlags.length) {
            <ul class="flags">
              @for (f of o.riskFlags; track f.code) {
                <li [attr.data-severity]="f.severity" [title]="f.message">
                  {{ flags[f.code] ? i18n.t(flags[f.code]) : f.message }}
                </li>
              }
            </ul>
          }

          <dl class="facts">
            <div>
              <dt>{{ i18n.t({ en: 'Installment / income', pt: 'Parcela / renda' }) }}</dt>
              <dd>
                {{ ratio(o.indicators['installmentToIncome']) }}
                <span class="muted"
                  >({{ i18n.t({ en: 'limit', pt: 'limite' }) }}
                  {{ ratio(o.indicators['installmentLimit']) }})</span
                >
              </dd>
            </div>
            @if (o.suggestedPlan; as plan) {
              <div>
                <dt>{{ i18n.t({ en: 'Plan that fits', pt: 'Plano que cabe' }) }}</dt>
                <dd>
                  {{ plan.installments }}× {{ i18n.money(plan.installmentAmount) }}
                  <span class="muted">({{ ratio(plan.installmentToIncome) }})</span>
                </dd>
              </div>
            }
            <div>
              <dt>{{ i18n.t({ en: 'Written by', pt: 'Escrito por' }) }}</dt>
              <dd class="mono">{{ o.model }} · {{ i18n.dateTime(o.generatedAt) }}</dd>
            </div>
          </dl>

          <p class="disclaimer muted">
            {{
              i18n.t({
                en: 'Advisory only. Risk and recommendation come from fixed policy rules; the model, if any, only writes the text. The decision belongs to a manager.',
                pt: 'Apenas consultivo. Risco e recomendação vêm de regras fixas da política; o modelo, quando há, só escreve o texto. A decisão é de um gestor.',
              })
            }}
          </p>
        </div>
      } @else if (proposal().status === 'SUBMITTED') {
        <p class="card waiting" role="status">
          @if (gaveUp()) {
            {{
              i18n.t({
                en: 'The AI worker has not answered yet. The opinion will appear when it does; a manager can also start the analysis without it.',
                pt: 'O worker de IA ainda não respondeu. O parecer aparece quando ele responder; um gestor também pode iniciar a análise sem ele.',
              })
            }}
          } @else {
            <span class="dot"></span>
            {{ i18n.t({ en: 'Drafting the opinion…', pt: 'Gerando o parecer…' }) }}
          }
        </p>
      } @else if (proposal().status === 'DRAFT') {
        <p class="muted hint">
          {{
            i18n.t({
              en: 'Drafted automatically when the proposal is submitted.',
              pt: 'Gerado automaticamente quando a proposta é enviada.',
            })
          }}
        </p>
      } @else {
        <p class="muted hint">—</p>
      }
    </section>
  `,
  styles: `
    .opinion {
      margin-top: 28px;
    }
    .card {
      margin-top: 12px;
      border-left: 4px solid var(--line-strong);
    }
    [data-recommendation='FAVORABLE'] .card {
      border-left-color: var(--green);
    }
    [data-recommendation='CAUTION'] .card {
      border-left-color: var(--yellow);
    }
    [data-recommendation='UNFAVORABLE'] .card {
      border-left-color: var(--red);
    }
    .verdict {
      margin: 0;
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: baseline;
      font-size: 18px;
    }
    .tag {
      font: 700 11px/1 var(--mono);
      text-transform: uppercase;
      letter-spacing: 0.08em;
      border: 1px solid currentColor;
      border-radius: 999px;
      padding: 4px 8px;
      color: var(--muted);
    }
    .summary {
      margin: 12px 0 0;
      line-height: 1.55;
    }
    .flags {
      list-style: none;
      padding: 0;
      margin: 14px 0 0;
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .flags li {
      font-size: 13px;
      padding: 5px 10px;
      border-radius: 999px;
      border: 1px solid currentColor;
      color: var(--muted);
    }
    .flags [data-severity='MEDIUM'] {
      color: var(--yellow);
    }
    .flags [data-severity='HIGH'] {
      color: var(--red);
    }
    .facts {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
      gap: 14px 24px;
      margin: 18px 0 0;
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
    .mono {
      font: 12px var(--mono);
    }
    .disclaimer {
      margin: 16px 0 0;
      font-size: 13px;
    }
    .waiting {
      display: flex;
      gap: 10px;
      align-items: center;
    }
    .dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: var(--orange);
      animation: pulse 1.2s ease-in-out infinite;
    }
    @keyframes pulse {
      50% {
        opacity: 0.25;
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .dot {
        animation: none;
      }
    }
    .hint {
      margin: 8px 0 0;
      font-size: 14px;
    }
  `,
})
export class AiOpinion {
  protected readonly i18n = inject(I18n);
  private readonly api = inject(ProposalsApi);

  readonly proposal = input.required<Proposal>();
  /** A current opinion arrived while the proposal was SUBMITTED: the page should reload the proposal. */
  readonly arrived = output<void>();

  protected readonly recommendations = RECOMMENDATION_LABEL;
  protected readonly risks = RISK_LABEL;
  protected readonly flags = FLAG_LABEL;
  protected readonly opinions = signal<Opinion[]>([]);
  protected readonly gaveUp = signal(false);
  /** The current opinion if there is one, else the latest (marked "previous submission"). */
  protected readonly shown = computed(() => {
    const all = this.opinions();
    return all.find((o) => o.current) ?? all[0] ?? null;
  });

  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
    effect(() => {
      const p = this.proposal();
      // re-run when the proposal moves (submit, return, …), not on unrelated changes
      void p.id;
      void p.status;
      void p.version;
      untracked(() => this.start(p));
    });
  }

  private start(p: Proposal): void {
    clearTimeout(this.timer);
    this.gaveUp.set(false);
    this.poll(p, 1);
  }

  private poll(p: Proposal, attempt: number): void {
    this.api.opinions(p.id).subscribe({
      next: (list) => {
        this.opinions.set(list);
        const waiting = p.status === 'SUBMITTED';
        if (waiting && list.some((o) => o.current)) {
          this.arrived.emit();
        } else if (waiting && attempt < MAX_POLLS) {
          this.timer = setTimeout(() => this.poll(p, attempt + 1), POLL_MS);
        } else if (waiting) {
          this.gaveUp.set(true);
        }
      },
      error: () => this.opinions.set([]),
    });
  }

  protected ratio(value: unknown): string {
    const n = typeof value === 'number' ? value : Number(value);
    if (!Number.isFinite(n)) return '—';
    return new Intl.NumberFormat(this.i18n.locale(), {
      style: 'percent',
      maximumFractionDigits: 1,
    }).format(n);
  }
}
