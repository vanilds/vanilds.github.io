import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  I18n,
  Page,
  PageSize,
  Pager,
  Problem,
  ProblemBanner,
  Proposal,
  ProposalFilter,
  ProposalStatus,
  ProposalsApi,
  STATUS_LABEL,
  Session,
  StatusBadge,
  toProblem,
} from '@creditdesk/shared';
import { Subject, catchError, debounceTime, of, switchMap, tap } from 'rxjs';
import { lastDays } from '../price-table';

@Component({
  selector: 'cd-proposal-list',
  imports: [ReactiveFormsModule, RouterLink, Pager, ProblemBanner, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-head">
      <div>
        <p class="kicker">{{ i18n.t({ en: 'Credit desk', pt: 'Mesa de crédito' }) }}</p>
        <h1>{{ i18n.t({ en: 'Proposals', pt: 'Propostas' }) }}</h1>
      </div>
      @if (session.can('ANALYST')) {
        <a
          class="btn btn-primary"
          routerLink="new"
          [queryParams]="customerId() ? { customerId: customerId() } : {}"
        >
          + {{ i18n.t({ en: 'New proposal', pt: 'Nova proposta' }) }}
        </a>
      }
    </header>

    <form class="toolbar" [formGroup]="filters" (submit)="$event.preventDefault()">
      <label class="field">
        Status
        <select formControlName="status">
          <option value="">{{ i18n.t({ en: 'All', pt: 'Todos' }) }}</option>
          @for (s of statuses; track s) {
            <option [value]="s">{{ i18n.t(labels[s]) }}</option>
          }
        </select>
      </label>
      <label class="field">
        {{ i18n.t({ en: 'From', pt: 'De' }) }}
        <input type="date" formControlName="from" />
      </label>
      <label class="field">
        {{ i18n.t({ en: 'To', pt: 'Até' }) }}
        <input type="date" formControlName="to" />
      </label>
      @if (session.can('ANALYST')) {
        <label class="check">
          <input type="checkbox" formControlName="mine" />
          {{ i18n.t({ en: 'Only mine', pt: 'Só as minhas' }) }}
        </label>
      }
    </form>

    @if (customerId()) {
      <p class="chip">
        {{ i18n.t({ en: 'Filtered by one customer', pt: 'Filtrado por um cliente' }) }}
        <a routerLink="." [queryParams]="{}">{{ i18n.t({ en: 'clear', pt: 'limpar' }) }}</a>
      </p>
    }

    <cd-problem-banner [problem]="problem()" />

    <div class="table-wrap" [attr.aria-busy]="loading()">
      <table class="grid">
        <thead>
          <tr>
            <th>{{ i18n.t({ en: 'Customer', pt: 'Cliente' }) }}</th>
            <th>{{ i18n.t({ en: 'Kind', pt: 'Tipo' }) }}</th>
            <th>Status</th>
            <th class="num">{{ i18n.t({ en: 'Amount', pt: 'Valor' }) }}</th>
            <th class="num">{{ i18n.t({ en: 'Installment', pt: 'Parcela' }) }}</th>
            <th>{{ i18n.t({ en: 'Analyst', pt: 'Analista' }) }}</th>
            <th>{{ i18n.t({ en: 'Updated', pt: 'Atualizada' }) }}</th>
          </tr>
        </thead>
        <tbody>
          @for (p of page()?.content ?? []; track p.id) {
            <tr>
              <td>
                <a [routerLink]="p.id">{{ p.customerName }}</a>
              </td>
              <td>
                {{
                  p.kind === 'NEW_CREDIT'
                    ? i18n.t({ en: 'New credit', pt: 'Crédito novo' })
                    : i18n.t({ en: 'Renegotiation', pt: 'Renegociação' })
                }}
              </td>
              <td><cd-status-badge [status]="p.status" /></td>
              <td class="num">{{ i18n.money(p.amount) }}</td>
              <td class="num">
                {{ p.installments ? p.installments + '× ' + i18n.money(p.installmentAmount) : '—' }}
              </td>
              <td>{{ p.analystName }}</td>
              <td class="nowrap">{{ i18n.dateTime(p.updatedAt) }}</td>
            </tr>
          } @empty {
            <tr>
              <td colspan="7" class="muted empty">
                {{
                  loading()
                    ? i18n.t({ en: 'Loading…', pt: 'Carregando…' })
                    : i18n.t({
                        en: 'No proposals in this period.',
                        pt: 'Nenhuma proposta neste período.',
                      })
                }}
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>

    @if (page(); as p) {
      <cd-pager
        [page]="p.page"
        [pages]="p.totalPages"
        [total]="p.totalElements"
        [size]="size()"
        (pageChange)="load($event)"
        (sizeChange)="size.set($event); load(0)"
      />
    }
  `,
  styles: `
    .check {
      display: flex;
      gap: 8px;
      align-items: center;
      font-size: 14px;
      padding-bottom: 10px;
    }
    .check input {
      width: auto;
    }
    .chip {
      font-size: 13px;
      color: var(--muted);
      margin: -4px 0 16px;
    }
    .empty {
      text-align: center;
      padding: 32px;
    }
    .nowrap {
      white-space: nowrap;
    }
  `,
})
export class ProposalList {
  protected readonly i18n = inject(I18n);
  protected readonly session = inject(Session);
  private readonly api = inject(ProposalsApi);

  /** ?customerId=… (link from the customers micro frontend), bound by withComponentInputBinding(). */
  readonly customerId = input<string | undefined>();

  protected readonly statuses = Object.keys(STATUS_LABEL) as ProposalStatus[];
  protected readonly labels = STATUS_LABEL;
  // The API refuses periods longer than 90 days (index-friendly queries); the screen starts with the last 30
  protected readonly filters = new FormGroup({
    status: new FormControl<ProposalStatus | ''>('', { nonNullable: true }),
    from: new FormControl(lastDays(30).from, { nonNullable: true }),
    to: new FormControl(lastDays(30).to, { nonNullable: true }),
    mine: new FormControl(false, { nonNullable: true }),
  });
  protected readonly page = signal<Page<Proposal> | null>(null);
  protected readonly size = signal<PageSize>(20);
  protected readonly loading = signal(false);
  protected readonly problem = signal<Problem | null>(null);

  private readonly requests = new Subject<ProposalFilter>();

  constructor() {
    this.requests
      .pipe(
        tap(() => this.loading.set(true)),
        switchMap((filter) =>
          this.api.search(filter).pipe(
            catchError((e) => {
              this.problem.set(toProblem(e));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        if (page) {
          this.page.set(page);
          this.problem.set(null);
        }
        this.loading.set(false);
      });

    this.filters.valueChanges
      .pipe(debounceTime(250), takeUntilDestroyed())
      .subscribe(() => this.load(0));
  }

  ngOnInit(): void {
    this.load(0);
  }

  ngOnChanges(): void {
    // the customerId query param changed (e.g. "clear" link): search again
    if (this.page()) this.load(0);
  }

  protected load(page: number): void {
    const f = this.filters.getRawValue();
    this.requests.next({
      status: f.status || undefined,
      from: f.from,
      to: f.to,
      mine: f.mine || undefined,
      customerId: this.customerId(),
      page,
      size: this.size(),
    });
  }
}
