import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18n } from '@creditdesk/shared';

@Component({
  selector: 'cd-signed-out',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="box">
      <p class="kicker">CreditDesk</p>
      <h1>{{ i18n.t({ en: 'You are signed out', pt: 'Você saiu' }) }}</h1>
      <p class="muted">
        {{
          i18n.t({
            en: 'Your Keycloak session was closed and the tokens were revoked.',
            pt: 'A sessão no Keycloak foi encerrada e os tokens foram revogados.',
          })
        }}
      </p>
      <div class="cta">
        <a class="btn btn-primary" routerLink="/demo">{{
          i18n.t({ en: 'Sign in again', pt: 'Entrar de novo' })
        }}</a>
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
    .cta {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-top: 28px;
    }
  `,
})
export class SignedOut {
  protected readonly i18n = inject(I18n);
}
