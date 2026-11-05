import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { I18n } from '@creditdesk/shared';
import { PROFILE } from '../content';

@Component({
  selector: 'cd-site-header',
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'no-print' },
  template: `
    <header class="bar">
      <a routerLink="/" class="brand" aria-label="Home">V<span>S</span></a>
      <nav>
        <a routerLink="/" fragment="work">{{ i18n.t({ en: 'Work', pt: 'Trabalho' }) }}</a>
        <a routerLink="/" fragment="creditdesk">CreditDesk</a>
        <a routerLink="/resume" routerLinkActive="active">{{
          i18n.t({ en: 'Resume', pt: 'Currículo' })
        }}</a>
        <a [href]="profile.links.github" target="_blank" rel="noopener">GitHub</a>
        <button
          type="button"
          class="lang"
          (click)="i18n.toggle()"
          [attr.aria-label]="i18n.t({ en: 'Mudar para português', pt: 'Switch to English' })"
        >
          {{ i18n.lang() === 'en' ? 'PT' : 'EN' }}
        </button>
      </nav>
    </header>
  `,
  styles: `
    .bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      max-width: var(--max);
      margin: 0 auto;
      padding: 20px var(--gutter);
    }
    .brand {
      font-weight: 800;
      font-size: 22px;
      color: var(--ink);
      text-decoration: none;
      letter-spacing: -0.04em;
    }
    .brand span {
      color: var(--orange);
    }
    nav {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 4px 18px;
      align-items: center;
      font-size: 14px;
    }
    nav a {
      color: var(--muted);
      text-decoration: none;
    }
    nav a:hover,
    nav a.active {
      color: var(--ink);
    }
    .lang {
      font: 700 12px/1 var(--mono);
      letter-spacing: 0.1em;
      color: var(--orange);
      background: transparent;
      border: 1px solid var(--line-strong);
      border-radius: 999px;
      padding: 7px 10px;
      cursor: pointer;
    }
    @media (max-width: 520px) {
      nav a[target] {
        display: none;
      }
    }
  `,
})
export class SiteHeader {
  protected readonly i18n = inject(I18n);
  protected readonly profile = PROFILE;
}
