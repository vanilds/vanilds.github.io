import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { I18n } from '@creditdesk/shared';
import { CERTIFICATIONS, EDUCATION, EXPERIENCE, LANGUAGES, PROFILE, SKILLS } from '../content';
import { SiteHeader } from './site-header';

/** One-page resume. "Download PDF" is the browser's print-to-PDF with print styles: always matches the site. */
@Component({
  selector: 'cd-resume',
  imports: [SiteHeader],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cd-site-header />
    <div class="tools no-print">
      <button type="button" class="btn btn-primary" (click)="print()">
        {{ i18n.t({ en: 'Download PDF', pt: 'Baixar PDF' }) }}
      </button>
      <span class="muted">{{
        i18n.t({
          en: 'Tip: switch EN / PT at the top before saving.',
          pt: 'Dica: troque EN / PT no topo antes de salvar.',
        })
      }}</span>
    </div>

    <article class="sheet">
      <header>
        <h1>{{ profile.fullName }}</h1>
        <p class="title">{{ i18n.t(profile.title) }} · {{ i18n.t(profile.location) }}</p>
        <p class="links">
          <a [href]="profile.links.linkedin">linkedin.com/in/vani-santos-9822a6b7</a> ·
          <a [href]="profile.links.github">github.com/vanilds</a> ·
          <a href="https://vanilds.github.io">vanilds.github.io</a>
        </p>
      </header>

      <section>
        <h2>{{ i18n.t({ en: 'Summary', pt: 'Resumo' }) }}</h2>
        <p>{{ i18n.t(profile.summary) }}</p>
      </section>

      <section>
        <h2>{{ i18n.t({ en: 'Experience', pt: 'Experiência' }) }}</h2>
        @for (job of experience; track job.company) {
          <div class="job">
            <div class="job-head">
              <h3>
                {{ job.company }}
                @if (job.client) {
                  <span> · {{ job.client }}</span>
                }
                — {{ i18n.t(job.role) }}
              </h3>
              <span class="period">{{ i18n.t(job.period) }}</span>
            </div>
            <ul>
              @for (p of job.points; track $index) {
                <li>{{ i18n.t(p) }}</li>
              }
            </ul>
            @if (job.stack.length) {
              <p class="stack">{{ job.stack.join(' · ') }}</p>
            }
          </div>
        }
      </section>

      <section>
        <h2>{{ i18n.t({ en: 'Portfolio project', pt: 'Projeto de portfólio' }) }}</h2>
        <div class="job">
          <div class="job-head">
            <h3>CreditDesk — vanilds.github.io</h3>
            <span class="period">2026</span>
          </div>
          <ul>
            <li>
              {{
                i18n.t({
                  en: 'Credit and renegotiation back-office: Angular shell + 2 micro frontends (Native Federation), Spring Boot 3 / Java 21 API, PostgreSQL, Keycloak (OIDC + PKCE).',
                  pt: 'Back-office de crédito e renegociação: shell Angular + 2 micro frontends (Native Federation), API Spring Boot 3 / Java 21, PostgreSQL, Keycloak (OIDC + PKCE).',
                })
              }}
            </li>
            <li>
              {{
                i18n.t({
                  en: 'Workflow with roles and four-eyes approval, optimistic locking (ETag / If-Match), append-only audit log, encrypted CPF/CNPJ, RFC 9457 errors, 100 automated tests (Testcontainers).',
                  pt: 'Fluxo com perfis e aprovação de quatro olhos, controle de concorrência (ETag / If-Match), auditoria imutável, CPF/CNPJ criptografados, erros RFC 9457, 100 testes automatizados (Testcontainers).',
                })
              }}
            </li>
            <li>
              {{
                i18n.t({
                  en: 'Kubernetes (k3s) on AWS EC2 provisioned by scripts, GitHub Actions with OIDC (no static keys), budget alerts.',
                  pt: 'Kubernetes (k3s) na AWS EC2 provisionado por scripts, GitHub Actions com OIDC (sem chaves fixas), alertas de orçamento.',
                })
              }}
            </li>
          </ul>
        </div>
      </section>

      <section class="two">
        <div>
          <h2>{{ i18n.t({ en: 'Skills', pt: 'Competências' }) }}</h2>
          @for (s of skills; track $index) {
            <p class="skill">
              <strong>{{ i18n.t(s.group) }}:</strong> {{ s.items.join(', ') }}
            </p>
          }
        </div>
        <div>
          <h2>{{ i18n.t({ en: 'Education', pt: 'Formação' }) }}</h2>
          @for (e of education; track $index) {
            <p class="skill">
              <strong>{{ i18n.t(e.title) }}</strong> — {{ i18n.t(e.detail) }}
            </p>
          }
          <h2>{{ i18n.t({ en: 'Certifications', pt: 'Certificações' }) }}</h2>
          <ul class="plain">
            @for (c of certifications; track $index) {
              <li>{{ i18n.t(c) }}</li>
            }
          </ul>
          <h2>{{ i18n.t({ en: 'Languages', pt: 'Idiomas' }) }}</h2>
          <ul class="plain">
            @for (l of languages; track $index) {
              <li>{{ i18n.t(l) }}</li>
            }
          </ul>
        </div>
      </section>
    </article>
  `,
  styles: `
    .tools,
    .sheet {
      width: min(860px, calc(100% - 2 * var(--gutter)));
      margin-inline: auto;
    }
    .tools {
      padding-bottom: 16px;
      display: flex;
      flex-wrap: wrap;
      gap: 14px;
      align-items: center;
      font-size: 13px;
    }
    .sheet {
      margin-bottom: 64px;
      padding: clamp(20px, 5vw, 48px);
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--radius);
      font-size: 15px;
    }
    h1 {
      font-size: clamp(28px, 5vw, 40px);
      font-weight: 800;
    }
    .title {
      margin: 6px 0 0;
      color: var(--orange);
      font-weight: 600;
    }
    .links {
      margin: 6px 0 0;
      font-size: 13px;
      color: var(--muted);
    }
    section {
      margin-top: 26px;
    }
    h2 {
      font: 700 12px/1 var(--mono);
      letter-spacing: 0.2em;
      text-transform: uppercase;
      color: var(--orange);
      border-bottom: 1px solid var(--line);
      padding-bottom: 8px;
      margin: 18px 0 12px;
    }
    section > h2:first-child,
    .two h2:first-child {
      margin-top: 0;
    }
    p {
      margin: 0;
    }
    .job + .job {
      margin-top: 16px;
    }
    .job-head {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 4px 16px;
    }
    h3 {
      font-size: 16px;
      font-weight: 700;
    }
    h3 span {
      font-weight: 500;
      color: var(--muted);
    }
    .period {
      font: 12px var(--mono);
      color: var(--muted);
      padding-top: 3px;
    }
    ul {
      margin: 6px 0 0;
      padding-left: 18px;
    }
    li + li {
      margin-top: 3px;
    }
    .stack {
      margin-top: 6px;
      font-size: 13px;
      color: var(--muted);
    }
    .two {
      display: grid;
      grid-template-columns: 1.2fr 1fr;
      gap: 28px;
    }
    .skill + .skill {
      margin-top: 6px;
    }
    .plain {
      padding-left: 18px;
    }
    @media (max-width: 700px) {
      .two {
        grid-template-columns: 1fr;
      }
    }
    @media print {
      .sheet {
        border: 0;
        margin: 0;
        padding: 0;
        width: auto;
        font-size: 11.5px;
        background: #fff;
      }
      section {
        margin-top: 14px;
        break-inside: avoid-page;
      }
      .job {
        break-inside: avoid;
      }
      a {
        color: inherit;
        text-decoration: none;
      }
    }
  `,
})
export class Resume {
  protected readonly i18n = inject(I18n);
  protected readonly profile = PROFILE;
  protected readonly experience = EXPERIENCE;
  protected readonly skills = SKILLS;
  protected readonly education = EDUCATION;
  protected readonly certifications = CERTIFICATIONS;
  protected readonly languages = LANGUAGES;

  protected print(): void {
    window.print();
  }
}
