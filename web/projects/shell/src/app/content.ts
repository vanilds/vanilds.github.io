import type { Text } from '@creditdesk/shared';

/*
 * Everything the portfolio and the resume say, in one file and two languages.
 * Edit here; the pages only lay it out.
 */

export const PROFILE = {
  name: 'Vani Santos',
  fullName: 'Vani Luiz dos Santos',
  title: { en: 'Senior Front-end Engineer', pt: 'Engenheira de Software Front-end Sênior' } as Text,
  location: { en: 'Osasco, São Paulo, Brazil · remote', pt: 'Osasco, São Paulo · remoto' } as Text,
  headline: {
    en: 'Angular and micro frontends for banking — now building the backend and the platform under them.',
    pt: 'Angular e micro frontends para bancos — agora construindo também o backend e a plataforma por baixo.',
  } as Text,
  summary: {
    en: 'Software engineer with 7+ years of front-end and 8+ in IT, almost all of it in Brazilian financial services (Itaú, Santander, Caixa, Bradesco). I build Angular applications that bank analysts and companies use every day: credit and renegotiation journeys, micro frontend shells, design systems. I care about code that a team can keep changing safely — typed contracts, tests and clear boundaries between apps.',
    pt: 'Engenheira de software com mais de 7 anos de front-end e 8 em TI, quase todos no setor financeiro (Itaú, Santander, Caixa, Bradesco). Construo aplicações Angular usadas todos os dias por analistas e empresas: jornadas de crédito e renegociação, shells de micro frontends, design systems. Me importo com código que o time consegue continuar mudando com segurança — contratos tipados, testes e fronteiras claras entre aplicações.',
  } as Text,
  links: {
    github: 'https://github.com/vanilds',
    linkedin: 'https://www.linkedin.com/in/vani-santos-9822a6b7/',
  },
};

export interface Job {
  company: string;
  client?: string;
  role: Text;
  period: Text;
  points: Text[];
  stack: string[];
}

export const EXPERIENCE: Job[] = [
  {
    company: 'Capgemini',
    client: 'Bradesco',
    role: { en: 'Senior Front-end Engineer', pt: 'Engenheira Front-end Sênior' },
    period: { en: 'Oct 2025 – present', pt: 'out 2025 – atual' },
    points: [
      {
        en: 'Micro frontends for the Bradesco digital menu ecosystem: Credit & Price Cockpit, PJ menus and the MPDPJ shell.',
        pt: 'Micro frontends do ecossistema Menu Digital Bradesco: Cockpit de Crédito e Preço, menus PJ e o shell MPDPJ.',
      },
      {
        en: 'Native Federation / Module Federation, Atomic Design and the Liquid design system.',
        pt: 'Native Federation / Module Federation, Atomic Design e o design system Liquid.',
      },
    ],
    stack: ['Angular', 'TypeScript', 'Native Federation', 'RxJS'],
  },
  {
    company: 'Itaú Unibanco',
    role: { en: 'Front-end Engineer', pt: 'Engenheira Front-end' },
    period: { en: '2021 – 2024', pt: '2021 – 2024' },
    points: [
      {
        en: 'Itaú Empresas: debt renegotiation journey for companies (PJ), from offer to contract.',
        pt: 'Itaú Empresas: jornada de renegociação de dívidas para empresas (PJ), da oferta à contratação.',
      },
      {
        en: 'Experimentation and analytics in production (GA4, VWO, Firebase), headless CMS content and Splunk monitoring.',
        pt: 'Experimentação e analytics em produção (GA4, VWO, Firebase), conteúdo via CMS headless e monitoramento com Splunk.',
      },
      {
        en: 'Delivered Angular features integrated with React Native apps and Java services on AWS.',
        pt: 'Entregas Angular integradas a apps React Native e serviços Java na AWS.',
      },
    ],
    stack: ['Angular', 'TypeScript', 'Java', 'AWS', 'GA4', 'Splunk'],
  },
  {
    company: 'everis (NTT DATA)',
    client: 'Santander',
    role: { en: 'Front-end Developer', pt: 'Desenvolvedora Front-end' },
    period: { en: '2021', pt: '2021' },
    points: [
      {
        en: 'CACEIS investment services platform for Santander — Angular front-end over Java services.',
        pt: 'Plataforma de serviços de investimento CACEIS para o Santander — front-end Angular sobre serviços Java.',
      },
    ],
    stack: ['Angular', 'TypeScript', 'Java'],
  },
  {
    company: 'Indra',
    client: 'Caixa Econômica Federal',
    role: { en: 'Developer', pt: 'Desenvolvedora' },
    period: { en: '2019 – 2021', pt: '2019 – 2021' },
    points: [
      {
        en: 'Migration of legacy systems to Caixa.Negócios, then the Dossiê Digital project.',
        pt: 'Migração de sistemas legados para o Caixa.Negócios e, depois, o projeto Dossiê Digital.',
      },
    ],
    stack: [],
  },
  {
    company: 'Pier8',
    role: { en: 'Full-stack Developer', pt: 'Desenvolvedora Full-stack' },
    period: { en: '2019', pt: '2019' },
    points: [
      {
        en: 'E-commerce logistics platform, front-end and back-end.',
        pt: 'Plataforma de logística para e-commerce, front-end e back-end.',
      },
    ],
    stack: [],
  },
];

export const SKILLS: { group: Text; items: string[] }[] = [
  {
    group: { en: 'Front-end', pt: 'Front-end' },
    items: [
      'Angular 9–22',
      'TypeScript',
      'RxJS',
      'NgRx',
      'Signals',
      'Native / Module Federation',
      'Angular Material',
      'Reactive Forms',
      'Sass',
      'Accessibility',
    ],
  },
  {
    group: { en: 'Back-end', pt: 'Back-end' },
    items: [
      'Java 21',
      'Spring Boot',
      'Spring Security',
      'JPA / Hibernate',
      'PostgreSQL',
      'Flyway',
      'REST / OpenAPI',
      'Keycloak (OIDC)',
    ],
  },
  {
    group: { en: 'Cloud & Ops', pt: 'Cloud & Ops' },
    items: [
      'AWS',
      'Azure',
      'Docker',
      'Kubernetes (k3s)',
      'GitHub Actions',
      'Azure DevOps',
      'GitLab CI',
      'SonarQube',
    ],
  },
  {
    group: { en: 'Quality', pt: 'Qualidade' },
    items: ['Jasmine / Karma', 'Vitest', 'JUnit 5', 'Testcontainers'],
  },
  {
    group: { en: 'AI', pt: 'IA' },
    items: ['LangGraph', 'smolagents', 'LlamaIndex', 'Prompt engineering'],
  },
];

export const EDUCATION: { title: Text; detail: Text }[] = [
  {
    title: { en: 'B.Sc. Computer Engineering', pt: 'Engenharia da Computação' },
    detail: { en: 'Universidade Paulista (UNIP), 2019', pt: 'Universidade Paulista (UNIP), 2019' },
  },
];

export const CERTIFICATIONS: Text[] = [
  { en: 'Microsoft Azure Fundamentals (AZ-900)', pt: 'Microsoft Azure Fundamentals (AZ-900)' },
  {
    en: 'Hugging Face Agents Course — final project on the GAIA benchmark',
    pt: 'Hugging Face Agents Course — projeto final no benchmark GAIA',
  },
  {
    en: 'Cybersecurity immersion (40 h) — 42 São Paulo, 2024',
    pt: 'Imersão em Cibersegurança (40 h) — 42 São Paulo, 2024',
  },
  { en: 'Prompt Engineering — FGV', pt: 'Engenharia de Prompt — FGV' },
];

export const LANGUAGES: Text[] = [
  { en: 'Portuguese — native', pt: 'Português — nativo' },
  {
    en: 'English — intermediate (reading and writing at work)',
    pt: 'Inglês — intermediário (leitura e escrita no trabalho)',
  },
];

export interface ProjectCard {
  name: string;
  url: string;
  text: Text;
  tags: string[];
}

export const PROJECTS: ProjectCard[] = [
  {
    name: 'gaia-agent-unit4',
    url: 'https://github.com/vanilds/gaia-agent-unit4',
    text: {
      en: 'AI agent for the GAIA benchmark (Hugging Face Agents Course final project) — 60% score.',
      pt: 'Agente de IA para o benchmark GAIA (projeto final do Hugging Face Agents Course) — 60% de acerto.',
    },
    tags: ['Python', 'LangGraph', 'smolagents'],
  },
];

/** Demo users (synthetic data). Same table as infra/keycloak/README.md. */
export const DEMO_USERS: { user: string; password: string; role: string; can: Text }[] = [
  {
    user: 'ana',
    password: 'ana-demo',
    role: 'ANALYST',
    can: {
      en: 'register customers, create and submit her proposals',
      pt: 'cadastra clientes, cria e envia as próprias propostas',
    },
  },
  {
    user: 'marcos',
    password: 'marcos-demo',
    role: 'MANAGER',
    can: {
      en: 'start analysis, approve, reject or return proposals of others',
      pt: 'inicia análise, aprova, reprova ou devolve propostas de outras pessoas',
    },
  },
  {
    user: 'aline',
    password: 'aline-demo',
    role: 'AUDITOR',
    can: {
      en: 'read everything, reveal a CPF with a reason',
      pt: 'lê tudo, revela um CPF com justificativa',
    },
  },
];
