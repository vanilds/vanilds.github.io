import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Customer,
  CustomersApi,
  I18n,
  Problem,
  ProblemBanner,
  Proposal,
  ProposalKind,
  ProposalsApi,
  Versioned,
  fieldErrors,
  toProblem,
} from '@creditdesk/shared';
import { catchError, debounceTime, map, of, startWith, switchMap } from 'rxjs';
import { previewInstallment } from '../price-table';

/**
 * New proposal (route /new, optional ?customerId=) or edit a draft (embedded in the detail page).
 * A draft may be incomplete; amount, installments and rate become mandatory only at "submit" (API rule).
 * The rate is typed as a percentage (1.99) and sent as a fraction (0.0199), the format of the contract.
 */
@Component({
  selector: 'cd-proposal-form',
  imports: [ReactiveFormsModule, RouterLink, ProblemBanner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!existing()) {
      <header class="page-head">
        <div>
          <a routerLink=".." class="back">← {{ i18n.t({ en: 'Proposals', pt: 'Propostas' }) }}</a>
          <h1>{{ i18n.t({ en: 'New proposal', pt: 'Nova proposta' }) }}</h1>
        </div>
      </header>
    }

    <cd-problem-banner [problem]="problem()" (reload)="reloadRequested.emit()" />

    <form class="card form" [formGroup]="form" (ngSubmit)="save()" novalidate>
      @if (!existing()) {
        @if (customer(); as c) {
          <div class="picked">
            <div>
              <span class="kicker">{{ i18n.t({ en: 'Customer', pt: 'Cliente' }) }}</span>
              <strong>{{ c.name }}</strong> <span class="mono muted">{{ c.document }}</span>
            </div>
            <button type="button" class="btn btn-ghost" (click)="customer.set(null)">
              {{ i18n.t({ en: 'Change', pt: 'Trocar' }) }}
            </button>
          </div>
        } @else {
          <label class="field">
            {{ i18n.t({ en: 'Customer (name)', pt: 'Cliente (nome)' }) }}
            <input [formControl]="customerSearch" type="search" autocomplete="off" />
          </label>
          <ul class="options" role="listbox">
            @for (c of matches(); track c.id) {
              <li>
                <button type="button" class="option" (click)="customer.set(c)">
                  {{ c.name }} <span class="mono muted">{{ c.document }}</span>
                </button>
              </li>
            }
          </ul>
        }
      }

      <fieldset class="kind">
        <legend class="sr-only">{{ i18n.t({ en: 'Kind', pt: 'Tipo' }) }}</legend>
        <label
          ><input type="radio" formControlName="kind" value="NEW_CREDIT" />
          {{ i18n.t({ en: 'New credit', pt: 'Crédito novo' }) }}</label
        >
        <label
          ><input type="radio" formControlName="kind" value="RENEGOTIATION" />
          {{ i18n.t({ en: 'Debt renegotiation', pt: 'Renegociação de dívida' }) }}</label
        >
      </fieldset>

      <div class="row">
        <label class="field">
          {{ i18n.t({ en: 'Amount (R$)', pt: 'Valor (R$)' }) }}
          <input
            formControlName="amount"
            type="number"
            min="100"
            max="1000000"
            step="0.01"
            inputmode="decimal"
            [attr.aria-invalid]="!!errors()['amount']"
          />
          @if (errors()['amount']; as e) {
            <span class="error">{{ e }}</span>
          }
        </label>
        <label class="field">
          {{ i18n.t({ en: 'Installments', pt: 'Parcelas' }) }}
          <input
            formControlName="installments"
            type="number"
            min="1"
            max="120"
            step="1"
            inputmode="numeric"
            [attr.aria-invalid]="!!errors()['installments']"
          />
          @if (errors()['installments']; as e) {
            <span class="error">{{ e }}</span>
          }
        </label>
        <label class="field">
          {{ i18n.t({ en: 'Rate (% a month)', pt: 'Taxa (% a.m.)' }) }}
          <input
            formControlName="ratePercent"
            type="number"
            min="0"
            max="20"
            step="0.01"
            inputmode="decimal"
            [attr.aria-invalid]="!!errors()['monthlyRate']"
          />
          @if (errors()['monthlyRate']; as e) {
            <span class="error">{{ e }}</span>
          }
        </label>
      </div>

      <p class="preview" aria-live="polite">
        @if (preview(); as value) {
          {{
            i18n.t({
              en: 'Estimated installment (Tabela Price):',
              pt: 'Parcela estimada (Tabela Price):',
            })
          }}
          <strong>{{ i18n.money(value) }}</strong>
          <span class="muted"
            >·
            {{
              i18n.t({
                en: 'the API calculates the final value',
                pt: 'o valor final é calculado pela API',
              })
            }}</span
          >
        } @else {
          <span class="muted">{{
            i18n.t({
              en: 'Fill amount, installments and rate to see the installment.',
              pt: 'Preencha valor, parcelas e taxa para ver a parcela.',
            })
          }}</span>
        }
      </p>

      <div class="actions">
        @if (existing()) {
          <button type="button" class="btn btn-ghost" (click)="cancelled.emit()">
            {{ i18n.t({ en: 'Cancel', pt: 'Cancelar' }) }}
          </button>
        }
        <button
          type="submit"
          class="btn btn-primary"
          [disabled]="(!existing() && !customer()) || saving()"
        >
          {{
            saving()
              ? i18n.t({ en: 'Saving…', pt: 'Salvando…' })
              : i18n.t({ en: 'Save draft', pt: 'Salvar rascunho' })
          }}
        </button>
      </div>
    </form>
  `,
  styles: `
    .back {
      font-size: 13px;
      text-decoration: none;
    }
    .form {
      display: grid;
      gap: 16px;
      max-width: 720px;
    }
    .row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: 16px;
    }
    .kind {
      display: flex;
      flex-wrap: wrap;
      gap: 20px;
      border: 0;
      padding: 0;
      margin: 0;
      font-size: 14px;
    }
    .kind label {
      display: flex;
      gap: 8px;
      align-items: center;
    }
    .kind input {
      width: auto;
    }
    .picked {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      padding: 12px 14px;
      border: 1px solid var(--line-strong);
      border-radius: var(--radius-sm);
    }
    .picked .kicker {
      display: block;
      margin-bottom: 4px;
    }
    .options {
      list-style: none;
      margin: -8px 0 0;
      padding: 0;
      display: grid;
      gap: 4px;
    }
    .option {
      width: 100%;
      text-align: left;
      background: var(--bg-raised);
      border: 1px solid var(--line);
      color: var(--ink);
      padding: 10px 12px;
      border-radius: var(--radius-sm);
      cursor: pointer;
      font: inherit;
      font-size: 14px;
    }
    .option:hover {
      border-color: var(--sky);
    }
    .preview {
      margin: 0;
      font-size: 14px;
    }
    .preview .muted,
    .picked .mono,
    .option .mono {
      margin-left: 8px;
    }
    .actions {
      display: flex;
      gap: 12px;
      justify-content: flex-end;
    }
  `,
})
export class ProposalForm {
  protected readonly i18n = inject(I18n);
  private readonly proposals = inject(ProposalsApi);
  private readonly customers = inject(CustomersApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** ?customerId=… when coming from a customer's page. */
  readonly customerId = input<string | undefined>();
  /** When set, edits this draft (sent with If-Match). */
  readonly existing = input<Versioned<Proposal> | null>(null);
  readonly saved = output<Versioned<Proposal>>();
  readonly cancelled = output<void>();
  readonly reloadRequested = output<void>();

  protected readonly form = new FormGroup({
    kind: new FormControl<ProposalKind>('NEW_CREDIT', { nonNullable: true }),
    amount: new FormControl<number | null>(null, [Validators.min(100), Validators.max(1_000_000)]),
    installments: new FormControl<number | null>(null, [Validators.min(1), Validators.max(120)]),
    ratePercent: new FormControl<number | null>(null, [Validators.min(0), Validators.max(20)]),
  });
  protected readonly customerSearch = new FormControl('', { nonNullable: true });
  protected readonly customer = signal<Customer | null>(null);
  protected readonly saving = signal(false);
  protected readonly problem = signal<Problem | null>(null);
  protected readonly errors = computed(() => fieldErrors(this.problem()));

  protected readonly matches = toSignal(
    this.customerSearch.valueChanges.pipe(
      debounceTime(250),
      switchMap((name) =>
        name.trim().length < 2
          ? of([])
          : this.customers.search({ name: name.trim(), size: 20 }).pipe(
              map((page) => page.content.slice(0, 6)),
              catchError(() => of([])),
            ),
      ),
    ),
    { initialValue: [] as Customer[] },
  );

  private readonly values = toSignal(this.form.valueChanges.pipe(startWith(this.form.value)), {
    initialValue: this.form.value,
  });
  protected readonly preview = computed(() => {
    const v = this.values();
    return previewInstallment(
      v.amount ?? null,
      v.ratePercent == null ? null : v.ratePercent / 100,
      v.installments ?? null,
    );
  });

  constructor() {
    effect(() => {
      const id = this.customerId();
      if (id && !this.existing()) {
        untracked(() =>
          this.customers
            .get(id)
            .subscribe({
              next: (r) => this.customer.set(r.value),
              error: () => this.customer.set(null),
            }),
        );
      }
    });
    effect(() => {
      const draft = this.existing()?.value;
      if (draft) {
        this.form.reset({
          kind: draft.kind,
          amount: draft.amount ?? null,
          installments: draft.installments ?? null,
          ratePercent: draft.monthlyRate == null ? null : round(draft.monthlyRate * 100, 4),
        });
        untracked(() => this.problem.set(null));
      }
    });
  }

  protected save(): void {
    const v = this.form.getRawValue();
    const body = {
      kind: v.kind,
      amount: v.amount ?? undefined,
      installments: v.installments ?? undefined,
      monthlyRate: v.ratePercent == null ? undefined : round(v.ratePercent / 100, 6),
    };
    const current = this.existing();
    const customer = this.customer();
    if (!current && !customer) return;
    this.saving.set(true);
    const request = current
      ? this.proposals.update(current.value.id, current.etag, body)
      : this.proposals.create({ ...body, customerId: customer!.id });
    request.subscribe({
      next: (result) => {
        this.saving.set(false);
        if (current) {
          this.saved.emit(result);
        } else {
          this.router.navigate(['..', result.value.id], { relativeTo: this.route });
        }
      },
      error: (e) => {
        this.saving.set(false);
        this.problem.set(toProblem(e));
      },
    });
  }
}

function round(value: number, digits: number): number {
  const f = Math.pow(10, digits);
  return Math.round(value * f) / f;
}
