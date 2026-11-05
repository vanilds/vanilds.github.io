import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Customer,
  CustomersApi,
  I18n,
  Problem,
  ProblemBanner,
  Session,
  Versioned,
  toProblem,
} from '@creditdesk/shared';
import { CustomerForm } from '../form/customer-form';

@Component({
  selector: 'cd-customer-detail',
  imports: [RouterLink, FormsModule, CustomerForm, ProblemBanner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-head">
      <div>
        <a routerLink=".." class="back">← {{ i18n.t({ en: 'Customers', pt: 'Clientes' }) }}</a>
        <h1>{{ record()?.value?.name ?? '…' }}</h1>
      </div>
      @if (record(); as r) {
        <div class="head-actions">
          <!-- Another micro frontend: linked by URL, never imported. Each app can be deployed on its own. -->
          <a
            class="btn"
            [routerLink]="['../../proposals']"
            [queryParams]="{ customerId: r.value.id }"
          >
            {{ i18n.t({ en: 'Proposals', pt: 'Propostas' }) }}
          </a>
          @if (session.can('ANALYST')) {
            <a
              class="btn btn-primary"
              [routerLink]="['../../proposals/new']"
              [queryParams]="{ customerId: r.value.id }"
            >
              + {{ i18n.t({ en: 'New proposal', pt: 'Nova proposta' }) }}
            </a>
          }
        </div>
      }
    </header>

    <cd-problem-banner [problem]="problem()" (reload)="load()" />

    @if (record(); as r) {
      @if (editing()) {
        <cd-customer-form
          [existing]="r"
          (saved)="record.set($event); editing.set(false)"
          (cancelled)="editing.set(false)"
          (reloadRequested)="load()"
        />
      } @else {
        <section class="card">
          <dl class="facts">
            <div>
              <dt>{{ i18n.t({ en: 'Type', pt: 'Tipo' }) }}</dt>
              <dd>
                {{
                  r.value.type === 'PF'
                    ? i18n.t({ en: 'Individual', pt: 'Pessoa física' })
                    : i18n.t({ en: 'Company', pt: 'Pessoa jurídica' })
                }}
              </dd>
            </div>
            <div>
              <dt>{{ r.value.type === 'PF' ? 'CPF' : 'CNPJ' }}</dt>
              <dd class="mono">{{ revealed() ?? r.value.document }}</dd>
            </div>
            <div>
              <dt>E-mail</dt>
              <dd>{{ r.value.email }}</dd>
            </div>
            <div>
              <dt>{{ i18n.t({ en: 'Monthly revenue', pt: 'Renda / faturamento' }) }}</dt>
              <dd>{{ i18n.money(r.value.monthlyRevenue) }}</dd>
            </div>
            <div>
              <dt>{{ i18n.t({ en: 'Created', pt: 'Criado em' }) }}</dt>
              <dd>{{ i18n.dateTime(r.value.createdAt) }}</dd>
            </div>
            <div>
              <dt>{{ i18n.t({ en: 'Version (ETag)', pt: 'Versão (ETag)' }) }}</dt>
              <dd class="mono">{{ r.etag }}</dd>
            </div>
          </dl>

          @if (session.can('ANALYST')) {
            <div class="actions">
              <button type="button" class="btn btn-danger" (click)="remove(r)" [disabled]="busy()">
                {{ i18n.t({ en: 'Delete', pt: 'Excluir' }) }}
              </button>
              <button type="button" class="btn" (click)="editing.set(true)">
                {{ i18n.t({ en: 'Edit', pt: 'Editar' }) }}
              </button>
            </div>
          }
        </section>

        @if (session.can('AUDITOR') && r.value.type === 'PF' && !revealed()) {
          <!-- LGPD: the full CPF is only shown to an auditor, with a reason that goes to the audit log -->
          <form class="card reveal" (ngSubmit)="reveal(r.value)">
            <label class="field">
              {{
                i18n.t({
                  en: 'Reason to see the full CPF (goes to the audit log)',
                  pt: 'Motivo para ver o CPF completo (vai para a auditoria)',
                })
              }}
              <input name="reason" [(ngModel)]="reason" required minlength="10" />
            </label>
            <button class="btn" type="submit" [disabled]="reason.trim().length < 10 || busy()">
              {{ i18n.t({ en: 'Reveal CPF', pt: 'Revelar CPF' }) }}
            </button>
          </form>
        }
      }
    }
  `,
  styles: `
    .back {
      font-size: 13px;
      text-decoration: none;
    }
    .head-actions,
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }
    .actions {
      justify-content: flex-end;
      margin-top: 20px;
    }
    .facts {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
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
      overflow-wrap: anywhere;
    }
    .reveal {
      margin-top: 16px;
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: end;
    }
    .reveal .field {
      flex: 1 1 320px;
    }
  `,
})
export class CustomerDetail {
  protected readonly i18n = inject(I18n);
  protected readonly session = inject(Session);
  private readonly api = inject(CustomersApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** Route parameter, bound by withComponentInputBinding(). */
  readonly id = input.required<string>();

  protected readonly record = signal<Versioned<Customer> | null>(null);
  protected readonly editing = signal(false);
  protected readonly busy = signal(false);
  protected readonly problem = signal<Problem | null>(null);
  protected readonly revealed = signal<string | null>(null);
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
      },
      error: (e) => this.problem.set(toProblem(e)),
    });
  }

  protected remove(r: Versioned<Customer>): void {
    const question = this.i18n.t({
      en: `Delete ${r.value.name}? Customers with open proposals cannot be deleted.`,
      pt: `Excluir ${r.value.name}? Clientes com propostas em aberto não podem ser excluídos.`,
    });
    if (!confirm(question)) return;
    this.busy.set(true);
    this.api.delete(r.value.id, r.etag).subscribe({
      next: () => this.router.navigate(['..'], { relativeTo: this.route }),
      error: (e) => {
        this.busy.set(false);
        this.problem.set(toProblem(e));
      },
    });
  }

  protected reveal(customer: Customer): void {
    this.busy.set(true);
    this.api.revealDocument(customer.id, this.reason.trim()).subscribe({
      next: (document) => {
        this.busy.set(false);
        this.revealed.set(document);
      },
      error: (e) => {
        this.busy.set(false);
        this.problem.set(toProblem(e));
      },
    });
  }
}
