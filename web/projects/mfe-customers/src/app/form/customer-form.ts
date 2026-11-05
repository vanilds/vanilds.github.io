import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Customer,
  CustomerType,
  CustomersApi,
  I18n,
  Problem,
  ProblemBanner,
  Versioned,
  fieldErrors,
  toProblem,
} from '@creditdesk/shared';

/**
 * Create (route /new) or edit (embedded in the detail page with [existing]).
 * Client-side checks are only for comfort; the API validates CPF/CNPJ check digits and answers with field errors,
 * which are shown under each input. One source of truth for the rules: the server.
 */
@Component({
  selector: 'cd-customer-form',
  imports: [ReactiveFormsModule, RouterLink, ProblemBanner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!existing()) {
      <header class="page-head">
        <div>
          <a routerLink=".." class="back">← {{ i18n.t({ en: 'Customers', pt: 'Clientes' }) }}</a>
          <h1>{{ i18n.t({ en: 'New customer', pt: 'Novo cliente' }) }}</h1>
        </div>
      </header>
    }

    <cd-problem-banner [problem]="problem()" (reload)="reloadRequested.emit()" />

    <form class="card form" [formGroup]="form" (ngSubmit)="save()" novalidate>
      @if (!existing()) {
        <fieldset class="type">
          <legend class="sr-only">
            {{ i18n.t({ en: 'Customer type', pt: 'Tipo de cliente' }) }}
          </legend>
          <label
            ><input type="radio" formControlName="type" value="PF" />
            {{ i18n.t({ en: 'Individual (CPF)', pt: 'Pessoa física (CPF)' }) }}</label
          >
          <label
            ><input type="radio" formControlName="type" value="PJ" />
            {{ i18n.t({ en: 'Company (CNPJ)', pt: 'Pessoa jurídica (CNPJ)' }) }}</label
          >
        </fieldset>

        <label class="field">
          {{ form.controls.type.value === 'PF' ? 'CPF' : 'CNPJ' }}
          <input
            formControlName="document"
            autocomplete="off"
            [attr.aria-invalid]="!!errors()['document']"
            [placeholder]="
              form.controls.type.value === 'PF' ? '000.000.000-00' : '00.000.000/0000-00'
            "
          />
          @if (errors()['document']; as e) {
            <span class="error">{{ e }}</span>
          }
        </label>
      }

      <label class="field">
        {{
          form.controls.type.value === 'PF'
            ? i18n.t({ en: 'Full name', pt: 'Nome completo' })
            : i18n.t({ en: 'Company name', pt: 'Razão social' })
        }}
        <input formControlName="name" autocomplete="off" [attr.aria-invalid]="!!errors()['name']" />
        @if (errors()['name']; as e) {
          <span class="error">{{ e }}</span>
        }
      </label>

      <label class="field">
        E-mail
        <input
          formControlName="email"
          type="email"
          autocomplete="off"
          [attr.aria-invalid]="!!errors()['email']"
        />
        @if (errors()['email']; as e) {
          <span class="error">{{ e }}</span>
        }
      </label>

      <label class="field">
        {{
          form.controls.type.value === 'PF'
            ? i18n.t({ en: 'Monthly income (R$)', pt: 'Renda mensal (R$)' })
            : i18n.t({ en: 'Monthly revenue (R$)', pt: 'Faturamento mensal (R$)' })
        }}
        <input
          formControlName="monthlyRevenue"
          type="number"
          min="0"
          step="0.01"
          inputmode="decimal"
          [attr.aria-invalid]="!!errors()['monthlyRevenue']"
        />
        @if (errors()['monthlyRevenue']; as e) {
          <span class="error">{{ e }}</span>
        }
      </label>

      <div class="actions">
        @if (existing()) {
          <button type="button" class="btn btn-ghost" (click)="cancelled.emit()">
            {{ i18n.t({ en: 'Cancel', pt: 'Cancelar' }) }}
          </button>
        }
        <button type="submit" class="btn btn-primary" [disabled]="form.invalid || saving()">
          {{
            saving()
              ? i18n.t({ en: 'Saving…', pt: 'Salvando…' })
              : i18n.t({ en: 'Save', pt: 'Salvar' })
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
      max-width: 560px;
    }
    .type {
      display: flex;
      flex-wrap: wrap;
      gap: 20px;
      border: 0;
      padding: 0;
      margin: 0;
      font-size: 14px;
    }
    .type label {
      display: flex;
      gap: 8px;
      align-items: center;
    }
    .type input {
      width: auto;
    }
    .actions {
      display: flex;
      gap: 12px;
      justify-content: flex-end;
    }
  `,
})
export class CustomerForm {
  protected readonly i18n = inject(I18n);
  private readonly api = inject(CustomersApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** When set, the form edits this customer (sent back with If-Match: its ETag). */
  readonly existing = input<Versioned<Customer> | null>(null);
  readonly saved = output<Versioned<Customer>>();
  readonly cancelled = output<void>();
  readonly reloadRequested = output<void>();

  protected readonly form = new FormGroup({
    type: new FormControl<CustomerType>('PF', { nonNullable: true }),
    document: new FormControl('', { nonNullable: true }),
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(150)],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    monthlyRevenue: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0)],
    }),
  });
  protected readonly saving = signal(false);
  protected readonly problem = signal<Problem | null>(null);
  protected readonly errors = computed(() => fieldErrors(this.problem()));

  constructor() {
    effect(() => {
      const current = this.existing();
      if (current) {
        const c = current.value;
        this.form.reset({
          type: c.type,
          document: c.document,
          name: c.name,
          email: c.email,
          monthlyRevenue: c.monthlyRevenue,
        });
        this.problem.set(null);
      } else {
        this.form.controls.document.addValidators(Validators.required);
      }
    });
  }

  protected save(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    const current = this.existing();
    this.saving.set(true);
    const request = current
      ? this.api.update(current.value.id, current.etag, {
          name: v.name,
          email: v.email,
          monthlyRevenue: v.monthlyRevenue ?? 0,
        })
      : this.api.create({
          type: v.type,
          document: v.document,
          name: v.name,
          email: v.email,
          monthlyRevenue: v.monthlyRevenue ?? 0,
        });
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
