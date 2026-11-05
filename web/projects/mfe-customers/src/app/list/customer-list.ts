import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  Customer,
  CustomerFilter,
  CustomersApi,
  I18n,
  Page,
  PageSize,
  Pager,
  Problem,
  ProblemBanner,
  Session,
  toProblem,
} from '@creditdesk/shared';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap, tap } from 'rxjs';

@Component({
  selector: 'cd-customer-list',
  imports: [ReactiveFormsModule, RouterLink, Pager, ProblemBanner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-head">
      <div>
        <p class="kicker">{{ i18n.t({ en: 'Registry', pt: 'Cadastro' }) }}</p>
        <h1>{{ i18n.t({ en: 'Customers', pt: 'Clientes' }) }}</h1>
      </div>
      @if (session.can('ANALYST')) {
        <a class="btn btn-primary" routerLink="new"
          >+ {{ i18n.t({ en: 'New customer', pt: 'Novo cliente' }) }}</a
        >
      }
    </header>

    <form class="toolbar" [formGroup]="filters" (submit)="$event.preventDefault()">
      <label class="field">
        {{ i18n.t({ en: 'Name', pt: 'Nome' }) }}
        <input formControlName="name" type="search" autocomplete="off" />
      </label>
      <label class="field">
        CPF / CNPJ
        <input formControlName="document" type="search" autocomplete="off" inputmode="text" />
      </label>
    </form>

    <cd-problem-banner [problem]="problem()" />

    <div class="table-wrap" [attr.aria-busy]="loading()">
      <table class="grid">
        <thead>
          <tr>
            <th>{{ i18n.t({ en: 'Name', pt: 'Nome' }) }}</th>
            <th>{{ i18n.t({ en: 'Type', pt: 'Tipo' }) }}</th>
            <th>CPF / CNPJ</th>
            <th>E-mail</th>
            <th class="num">{{ i18n.t({ en: 'Monthly revenue', pt: 'Renda / faturamento' }) }}</th>
          </tr>
        </thead>
        <tbody>
          @for (c of page()?.content ?? []; track c.id) {
            <tr>
              <td>
                <a [routerLink]="c.id">{{ c.name }}</a>
              </td>
              <td>{{ c.type }}</td>
              <td class="mono">{{ c.document }}</td>
              <td>{{ c.email }}</td>
              <td class="num">{{ i18n.money(c.monthlyRevenue) }}</td>
            </tr>
          } @empty {
            <tr>
              <td colspan="5" class="muted empty">
                {{
                  loading()
                    ? i18n.t({ en: 'Loading…', pt: 'Carregando…' })
                    : i18n.t({ en: 'No customers found.', pt: 'Nenhum cliente encontrado.' })
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
        (pageChange)="load({ page: $event })"
        (sizeChange)="size.set($event); load({ page: 0 })"
      />
    }
  `,
  styles: `
    .empty {
      text-align: center;
      padding: 32px;
    }
  `,
})
export class CustomerList {
  protected readonly i18n = inject(I18n);
  protected readonly session = inject(Session);
  private readonly api = inject(CustomersApi);

  protected readonly filters = new FormGroup({
    name: new FormControl('', { nonNullable: true }),
    document: new FormControl('', { nonNullable: true }),
  });
  protected readonly page = signal<Page<Customer> | null>(null);
  protected readonly size = signal<PageSize>(20);
  protected readonly loading = signal(false);
  protected readonly problem = signal<Problem | null>(null);

  private readonly requests = new Subject<CustomerFilter>();

  constructor() {
    // switchMap: typing fast cancels the previous search, so an old answer never overwrites a newer one
    this.requests
      .pipe(
        tap(() => this.loading.set(true)),
        // switchMap: typing fast cancels the previous search, so an old answer never overwrites a newer one.
        // catchError inside: one failed search must not end the stream for the next ones.
        switchMap((filter) =>
          this.api.search(filter).pipe(
            catchError((e) => {
              this.problem.set(toProblem(e));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((page) => {
        if (page) {
          this.page.set(page);
          this.problem.set(null);
        }
        this.loading.set(false);
      });

    this.filters.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.load({ page: 0 }));

    this.load({ page: 0 });
  }

  protected load(change: { page: number }): void {
    const { name, document } = this.filters.getRawValue();
    this.requests.next({
      name: name.trim(),
      document: document.trim(),
      page: change.page,
      size: this.size(),
    });
  }
}
