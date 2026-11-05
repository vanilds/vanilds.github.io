import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { I18n } from '../i18n';
import type { Problem } from '../problem';

/** Shows an RFC 9457 problem the way the API wrote it, plus the correlation id to find it in the logs. */
@Component({
  selector: 'cd-problem-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (problem(); as p) {
      <div class="banner" role="alert">
        <div>
          <strong>{{ p.title }}</strong>
          @if (p.detail) {
            <p>{{ p.detail }}</p>
          }
          @if (p.stale) {
            <p>
              {{
                i18n.t({
                  en: 'Someone saved this record after you opened it. Reload to see their changes, then try again.',
                  pt: 'Alguém salvou este registro depois que você abriu. Recarregue para ver as mudanças e tente de novo.',
                })
              }}
            </p>
          }
          @if (p.correlationId) {
            <p class="trace mono">X-Correlation-Id: {{ p.correlationId }}</p>
          }
        </div>
        @if (p.stale) {
          <button type="button" class="btn" (click)="reload.emit()">
            {{ i18n.t({ en: 'Reload', pt: 'Recarregar' }) }}
          </button>
        }
      </div>
    }
  `,
  styles: `
    .banner {
      display: flex;
      gap: 16px;
      align-items: start;
      justify-content: space-between;
      border: 1px solid color-mix(in srgb, var(--red) 60%, transparent);
      background: color-mix(in srgb, var(--red) 10%, var(--surface));
      border-radius: var(--radius);
      padding: 14px 16px;
      margin-bottom: 16px;
      font-size: 14px;
    }
    p {
      margin: 4px 0 0;
      color: var(--ink);
    }
    .trace {
      color: var(--muted);
      font-size: 12px;
    }
  `,
})
export class ProblemBanner {
  protected readonly i18n = inject(I18n);
  readonly problem = input<Problem | null>(null);
  readonly reload = output<void>();
}
