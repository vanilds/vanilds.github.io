import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18n } from '@creditdesk/shared';
import { DEMO_USERS, EXPERIENCE, PROFILE, PROJECTS, SKILLS } from '../content';
import { SiteHeader } from './site-header';

@Component({
  selector: 'cd-home',
  imports: [RouterLink, SiteHeader],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cd-site-header />

    <main>
      <section class="hero wrap">
        <p class="kicker">
          {{ i18n.t(profile.title) }} · {{ i18n.t({ en: 'Banking', pt: 'Setor financeiro' }) }}
        </p>
        <h1>{{ profile.name }}<span class="dot">.</span></h1>
        <p class="lead">{{ i18n.t(profile.headline) }}</p>
        <div class="cta">
          <a class="btn btn-primary" routerLink="/demo"
            >{{ i18n.t({ en: 'Open the CreditDesk demo', pt: 'Abrir a demo CreditDesk' }) }} →</a
          >
          <a class="btn" routerLink="/resume">{{ i18n.t({ en: 'Resume', pt: 'Currículo' }) }}</a>
          <a class="btn btn-ghost" [href]="profile.links.linkedin" target="_blank" rel="noopener"
            >LinkedIn ↗</a
          >
        </div>
        <ul class="stats" aria-label="Highlights">
          <li>
            <strong>7+</strong
            ><span>{{ i18n.t({ en: 'years of front-end', pt: 'anos de front-end' }) }}</span>
          </li>
          <li>
            <strong>4</strong
            ><span>{{ i18n.t({ en: 'Brazilian banks', pt: 'bancos brasileiros' }) }}</span>
          </li>
          <li>
            <strong>MFE</strong
            ><span>{{
              i18n.t({ en: 'micro frontends in production', pt: 'micro frontends em produção' })
            }}</span>
          </li>
        </ul>
      </section>

      <section id="work" class="wrap section">
        <p class="kicker">01 · {{ i18n.t({ en: 'Experience', pt: 'Experiência' }) }}</p>
        <h2>
          {{
            i18n.t({
              en: 'Products bank teams use every day',
              pt: 'Produtos que times de banco usam todo dia',
            })
          }}
        </h2>
        <ol class="timeline">
          @for (job of experience; track job.company) {
            <li>
              <div class="when mono">{{ i18n.t(job.period) }}</div>
              <div>
                <h3>
                  {{ job.company }}
                  @if (job.client) {
                    <span class="muted"> · {{ job.client }}</span>
                  }
                </h3>
                <p class="role">{{ i18n.t(job.role) }}</p>
                <ul class="points">
                  @for (p of job.points; track $index) {
                    <li>{{ i18n.t(p) }}</li>
                  }
                </ul>
              </div>
            </li>
          }
        </ol>
      </section>

      <section id="creditdesk" class="wrap section">
        <p class="kicker">02 · {{ i18n.t({ en: 'Case study', pt: 'Estudo de caso' }) }}</p>
        <h2>CreditDesk</h2>
        <p class="lead small">
          {{
            i18n.t({
              en: 'A back-office for credit and debt-renegotiation proposals, built end to end to show the whole path: Angular micro frontends, a Spring Boot API, Keycloak, and Kubernetes on AWS. All data is synthetic.',
              pt: 'Um back-office de propostas de crédito e renegociação, construído de ponta a ponta para mostrar o caminho todo: micro frontends Angular, API Spring Boot, Keycloak e Kubernetes na AWS. Todos os dados são sintéticos.',
            })
          }}
        </p>

        <div class="features">
          <article>
            <h3>{{ i18n.t({ en: 'Micro frontends', pt: 'Micro frontends' }) }}</h3>
            <p>
              {{
                i18n.t({
                  en: 'This page is the shell. Customers and Proposals are separate Angular apps, loaded at runtime with Native Federation — fetched only when you open the demo.',
                  pt: 'Esta página é o shell. Clientes e Propostas são apps Angular separadas, carregadas em tempo de execução com Native Federation — só quando você abre a demo.',
                })
              }}
            </p>
          </article>
          <article>
            <h3>{{ i18n.t({ en: 'Real workflow rules', pt: 'Regras reais de fluxo' }) }}</h3>
            <p>
              {{
                i18n.t({
                  en: 'Roles from Keycloak, four-eyes approval, a reason for every rejection, and optimistic locking with ETag / If-Match so nobody overwrites a colleague.',
                  pt: 'Perfis do Keycloak, aprovação com quatro olhos, motivo obrigatório para reprovar e controle de concorrência com ETag / If-Match para ninguém sobrescrever o colega.',
                })
              }}
            </p>
          </article>
          <article>
            <h3>{{ i18n.t({ en: 'Privacy by design', pt: 'Privacidade desde o projeto' }) }}</h3>
            <p>
              {{
                i18n.t({
                  en: 'CPF and CNPJ are encrypted (AES-GCM) and searched by HMAC. Only an auditor sees a full CPF, with a reason written to an append-only audit log.',
                  pt: 'CPF e CNPJ ficam criptografados (AES-GCM) e são buscados por HMAC. Só a auditoria vê um CPF completo, com motivo gravado num log de auditoria imutável.',
                })
              }}
            </p>
          </article>
          <article>
            <h3>{{ i18n.t({ en: 'Typed contract', pt: 'Contrato tipado' }) }}</h3>
            <p>
              {{
                i18n.t({
                  en: 'The front-end types are generated from the API’s OpenAPI file. If the API changes a field, the front stops compiling — in CI, not in production.',
                  pt: 'Os tipos do front são gerados a partir do OpenAPI da API. Se a API muda um campo, o front para de compilar — no CI, não em produção.',
                })
              }}
            </p>
          </article>
        </div>

        <figure class="diagram">
          <img
            src="creditdesk-system.png"
            width="3200"
            height="1410"
            loading="lazy"
            [alt]="
              i18n.t({
                en: 'CreditDesk architecture: Angular shell and micro frontends, Spring Boot API, Keycloak, PostgreSQL, RabbitMQ and an AI worker on Kubernetes',
                pt: 'Arquitetura do CreditDesk: shell Angular e micro frontends, API Spring Boot, Keycloak, PostgreSQL, RabbitMQ e um worker de IA no Kubernetes',
              })
            "
          />
        </figure>

        <div class="try card">
          <div>
            <h3>{{ i18n.t({ en: 'Try it', pt: 'Experimente' }) }}</h3>
            <p class="muted">
              {{
                i18n.t({
                  en: 'The demo runs on a small AWS server, weekdays 08:00–20:00 (São Paulo). Sign in with any of these users:',
                  pt: 'A demo roda num servidor pequeno na AWS, em dias úteis das 08:00 às 20:00 (horário de São Paulo). Entre com um destes usuários:',
                })
              }}
            </p>
          </div>
          <table class="grid users">
            <thead>
              <tr>
                <th>{{ i18n.t({ en: 'User', pt: 'Usuário' }) }}</th>
                <th>{{ i18n.t({ en: 'Password', pt: 'Senha' }) }}</th>
                <th>{{ i18n.t({ en: 'Role', pt: 'Perfil' }) }}</th>
                <th>{{ i18n.t({ en: 'Can', pt: 'Pode' }) }}</th>
              </tr>
            </thead>
            <tbody>
              @for (u of demoUsers; track u.user) {
                <tr>
                  <td class="mono">{{ u.user }}</td>
                  <td class="mono">{{ u.password }}</td>
                  <td class="mono">{{ u.role }}</td>
                  <td>{{ i18n.t(u.can) }}</td>
                </tr>
              }
            </tbody>
          </table>
          <div class="cta">
            <a class="btn btn-primary" routerLink="/demo"
              >{{ i18n.t({ en: 'Open the demo', pt: 'Abrir a demo' }) }} →</a
            >
            <a
              class="btn"
              href="https://github.com/vanilds/vanilds.github.io"
              target="_blank"
              rel="noopener"
              >{{ i18n.t({ en: 'Source code and decisions', pt: 'Código e decisões' }) }} ↗</a
            >
          </div>
        </div>
      </section>

      <section class="wrap section">
        <p class="kicker">03 · {{ i18n.t({ en: 'Toolbox', pt: 'Ferramentas' }) }}</p>
        <h2>
          {{
            i18n.t({
              en: 'Front-end depth, full-stack reach',
              pt: 'Profundidade no front, alcance full-stack',
            })
          }}
        </h2>
        <div class="skills">
          @for (s of skills; track $index) {
            <div>
              <h3 class="mono">{{ i18n.t(s.group) }}</h3>
              <ul class="tags">
                @for (item of s.items; track item) {
                  <li>{{ item }}</li>
                }
              </ul>
            </div>
          }
        </div>
      </section>

      <section class="wrap section">
        <p class="kicker">04 · {{ i18n.t({ en: 'Other projects', pt: 'Outros projetos' }) }}</p>
        <div class="projects">
          @for (p of projects; track p.name) {
            <a class="card project" [href]="p.url" target="_blank" rel="noopener">
              <h3 class="mono">{{ p.name }} ↗</h3>
              <p>{{ i18n.t(p.text) }}</p>
              <ul class="tags">
                @for (t of p.tags; track t) {
                  <li>{{ t }}</li>
                }
              </ul>
            </a>
          }
        </div>
      </section>

      <footer class="wrap foot">
        <p class="kicker">{{ i18n.t({ en: 'Let’s talk', pt: 'Vamos conversar' }) }}</p>
        <p class="lead small">
          {{
            i18n.t({
              en: 'Open to remote senior front-end or full-stack roles, in Brazil or abroad.',
              pt: 'Aberta a vagas remotas de front-end sênior ou full-stack, no Brasil ou no exterior.',
            })
          }}
        </p>
        <div class="cta">
          <a class="btn btn-primary" [href]="profile.links.linkedin" target="_blank" rel="noopener"
            >LinkedIn ↗</a
          >
          <a class="btn" [href]="profile.links.github" target="_blank" rel="noopener">GitHub ↗</a>
        </div>
        <p class="muted fine">
          © {{ year }} {{ profile.fullName }} ·
          {{
            i18n.t({
              en: 'Built with Angular, served by GitHub Pages.',
              pt: 'Feito em Angular, publicado no GitHub Pages.',
            })
          }}
        </p>
      </footer>
    </main>
  `,
  styleUrl: './home.scss',
})
export class Home {
  protected readonly i18n = inject(I18n);
  protected readonly profile = PROFILE;
  protected readonly experience = EXPERIENCE;
  protected readonly skills = SKILLS;
  protected readonly projects = PROJECTS;
  protected readonly demoUsers = DEMO_USERS;
  protected readonly year = new Date().getFullYear();
}
