import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { I18n, Session } from '@creditdesk/shared';

/** The demo's frame. The pages inside the outlet come from the micro frontends. */
@Component({
  selector: 'cd-demo-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="top">
      <a routerLink="/" class="brand">Credit<span>Desk</span></a>
      <nav [attr.aria-label]="i18n.t({ en: 'Demo sections', pt: 'Seções da demo' })">
        <a routerLink="proposals" routerLinkActive="active">{{
          i18n.t({ en: 'Proposals', pt: 'Propostas' })
        }}</a>
        <a routerLink="customers" routerLinkActive="active">{{
          i18n.t({ en: 'Customers', pt: 'Clientes' })
        }}</a>
      </nav>
      <div class="me">
        @if (session.user(); as u) {
          <span class="who"
            ><strong>{{ u.name }}</strong>
            <span class="role mono">{{ u.roles.join(' · ') }}</span></span
          >
        }
        <button type="button" class="btn btn-ghost lang" (click)="i18n.toggle()">
          {{ i18n.lang() === 'en' ? 'PT' : 'EN' }}
        </button>
        <button type="button" class="btn" (click)="session.signOut()">
          {{ i18n.t({ en: 'Sign out', pt: 'Sair' }) }}
        </button>
      </div>
    </header>
    <p class="notice">
      {{
        i18n.t({
          en: 'Demo with synthetic data. Customers and Proposals are separate Angular apps loaded at runtime.',
          pt: 'Demo com dados sintéticos. Clientes e Propostas são apps Angular separadas, carregadas em tempo de execução.',
        })
      }}
    </p>
    <main><router-outlet /></main>
  `,
  styles: `
    :host {
      display: block;
      min-height: 100vh;
    }
    .top {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px 28px;
      padding: 14px var(--gutter);
      border-bottom: 1px solid var(--line);
      background: var(--bg-raised);
      position: sticky;
      top: 0;
      z-index: 10;
    }
    .brand {
      font-weight: 800;
      font-size: 19px;
      color: var(--ink);
      text-decoration: none;
      letter-spacing: -0.03em;
    }
    .brand span {
      color: var(--orange);
    }
    nav {
      display: flex;
      gap: 4px;
    }
    nav a {
      color: var(--muted);
      text-decoration: none;
      font-size: 14px;
      font-weight: 600;
      padding: 8px 12px;
      border-radius: var(--radius-sm);
    }
    nav a:hover {
      color: var(--ink);
    }
    nav a.active {
      color: var(--ink);
      background: var(--surface-2);
    }
    .me {
      margin-left: auto;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 10px;
      font-size: 14px;
    }
    .who {
      display: inline-flex;
      gap: 8px;
      align-items: baseline;
    }
    .role {
      font-size: 11px;
      color: var(--orange);
      letter-spacing: 0.08em;
    }
    .lang {
      font-family: var(--mono);
    }
    .notice {
      margin: 0;
      padding: 8px var(--gutter);
      font-size: 12px;
      color: var(--muted);
      border-bottom: 1px solid var(--line);
    }
    main {
      max-width: var(--max);
      margin: 0 auto;
      padding: 28px var(--gutter) 64px;
    }
    @media (max-width: 640px) {
      .who {
        display: none;
      }
    }
  `,
})
export class DemoLayout {
  protected readonly i18n = inject(I18n);
  protected readonly session = inject(Session);
}
