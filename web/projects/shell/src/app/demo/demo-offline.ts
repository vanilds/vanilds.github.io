import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18n } from '@creditdesk/shared';

/**
 * The cluster only runs on weekdays 08:00–20:00 São Paulo (to fit a US$ 100 credit for a year).
 * Outside that window the portfolio still works and this page explains why the demo does not.
 */
@Component({
  selector: 'cd-demo-offline',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="box">
      <p class="kicker">CreditDesk · {{ i18n.t({ en: 'offline', pt: 'fora do ar' }) }}</p>
      <h1>{{ i18n.t({ en: 'The demo is sleeping', pt: 'A demo está dormindo' }) }}</h1>
      <p class="muted">
        {{
          i18n.t({
            en: 'To keep costs low, the demo server runs on weekdays from 08:00 to 20:00 (São Paulo time). It is switched off right now, or still starting up.',
            pt: 'Para manter o custo baixo, o servidor da demo funciona em dias úteis, das 08:00 às 20:00 (horário de São Paulo). Agora ele está desligado ou ainda ligando.',
          })
        }}
      </p>
      <p class="muted">
        {{
          i18n.t({
            en: 'Meanwhile, the code, the architecture decisions and the screenshots are on GitHub.',
            pt: 'Enquanto isso, o código, as decisões de arquitetura e as imagens estão no GitHub.',
          })
        }}
      </p>
      <div class="cta">
        <a class="btn btn-primary" href="/demo">{{
          i18n.t({ en: 'Try again', pt: 'Tentar de novo' })
        }}</a>
        <a
          class="btn"
          href="https://github.com/vanilds/vanilds.github.io"
          target="_blank"
          rel="noopener"
          >GitHub ↗</a
        >
        <a class="btn btn-ghost" routerLink="/">{{
          i18n.t({ en: 'Back to portfolio', pt: 'Voltar ao portfólio' })
        }}</a>
      </div>
    </main>
  `,
  styles: `
    .box {
      max-width: 640px;
      margin: 0 auto;
      padding: clamp(48px, 14vh, 140px) var(--gutter);
    }
    h1 {
      font-size: clamp(32px, 6vw, 52px);
      font-weight: 800;
      margin: 14px 0 18px;
    }
    p {
      margin: 0 0 12px;
    }
    .cta {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-top: 28px;
    }
  `,
})
export class DemoOffline {
  protected readonly i18n = inject(I18n);
}
