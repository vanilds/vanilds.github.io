import type { Text } from '@creditdesk/shared';

/*
 * Everything the portfolio and the resume say, in one file and two languages.
 * Source of truth: the user's CV (CV-VaniSantos.pdf) and LinkedIn profile, kept consistent with both.
 * Edit here; the pages only lay it out.
 */

export const PROFILE = {
  name: 'Vani Santos',
  fullName: 'Vani Luiz dos Santos',
  photo: 'vani-santos.jpg',
  title: {
    en: 'Senior Front-end Software Engineer',
    pt: 'Engenheira de Software Front-end Sênior',
  } as Text,
  /** Same keywords as the LinkedIn headline. */
  tagline: {
    en: 'Applied AI · Angular, TypeScript & Micro Frontends · React Native · AWS',
    pt: 'Inteligência Artificial · Angular, TypeScript & Microfrontends · React Native · AWS',
  } as Text,
  location: {
    en: 'Osasco, São Paulo, Brazil · remote',
    pt: 'Osasco – SP, Brasil · remoto',
  } as Text,
  headline: {
    en: 'Angular and micro frontends for Brazilian banks — now building the backend and the platform under them, with AI as a daily engineering tool.',
    pt: 'Angular e microfrontends para bancos brasileiros — agora construindo também o backend e a plataforma por baixo, com IA como ferramenta de engenharia no dia a dia.',
  } as Text,
  summary: {
    en: 'Senior Front-end Software Engineer with 8+ years in technology, applying AI to software development and specialised in Angular, TypeScript, micro frontends and scalable digital products for the financial sector (Bradesco, Itaú, Caixa and Santander). I use AI to support the technical analysis of demands, failure investigation, code review and the evaluation of implementation alternatives — always with human validation. Experience in application architecture, reusable components, REST APIs, RxJS, state management, CI/CD, observability, performance and code reviews, working with Product, Design, Back-end, iOS and Android. Expanding into mobile with React Native, Swift and SwiftUI.',
    pt: 'Engenheira de Software Front-end Sênior com mais de 8 anos em tecnologia, atuando com Inteligência Artificial aplicada ao desenvolvimento de software e forte especialização em Angular, TypeScript, microfrontends e produtos digitais escaláveis para o setor financeiro (Bradesco, Itaú, Caixa e Santander). Uso IA como apoio à análise técnica de demandas, investigação de falhas, revisão de código e avaliação de alternativas de implementação, sempre com validação humana. Experiência em arquitetura de aplicações, componentes reutilizáveis, APIs REST, RxJS, gerenciamento de estado, CI/CD, observabilidade, performance e code reviews, em colaboração com Produto, Design, Back-end, iOS e Android. Ampliando a atuação mobile com React Native, Swift e SwiftUI.',
  } as Text,
  lookingFor: {
    en: 'Open to Senior Front-end Engineer, Angular Developer, Software Engineer or Mobile Developer roles — remote, in Brazil or abroad.',
    pt: 'Busco oportunidades como Engenheira Front-end Sênior, Desenvolvedora Angular, Engenheira de Software ou Desenvolvedora Mobile — remoto, no Brasil ou no exterior.',
  } as Text,
  links: {
    github: 'https://github.com/vanilds',
    linkedin: 'https://www.linkedin.com/in/vani-santos-9822a6b7/',
    linkedinPosts: 'https://www.linkedin.com/in/vani-santos-9822a6b7/recent-activity/all/',
  },
};

export interface Job {
  company: string;
  client?: string;
  role: Text;
  period: Text;
  place?: Text;
  points: Text[];
  stack: string[];
}

export const EXPERIENCE: Job[] = [
  {
    company: 'Capgemini',
    client: 'Bradesco',
    role: {
      en: 'Senior Front-end Engineering Analyst',
      pt: 'Analista de Engenharia Front-End Sênior',
    },
    period: { en: 'Oct 2025 – present', pt: 'out/2025 – atual' },
    place: { en: 'Remote', pt: 'Remoto' },
    points: [
      {
        en: 'Micro frontends for the Bradesco Menu Digital ecosystem (Credit & Price Cockpit, PSPJ Menu, PPJ Menu and the MPDPJ shell) with Native Federation, Module Federation, reusable components and the Liquid design system.',
        pt: 'Microfrontends do ecossistema Menu Digital do Bradesco (Cockpit de Crédito e Preço, Menu PSPJ, Menu PPJ e shell MPDPJ) com Native Federation, Module Federation, componentes reutilizáveis e Design System Liquid.',
      },
      {
        en: 'AI as support for technical analysis, failure investigation, code review and evaluating implementation alternatives, with human validation before anything reaches the Angular solutions.',
        pt: 'IA como apoio à análise técnica de demandas, investigação de falhas, revisão de código e avaliação de alternativas de implementação, com validação humana antes da aplicação nas soluções Angular.',
      },
      {
        en: 'Scalable applications with Angular, TypeScript, RxJS, HTML and Sass, integrated with REST APIs and Java / Spring Boot back-end teams.',
        pt: 'Aplicações escaláveis com Angular, TypeScript, RxJS, HTML e Sass, integradas a APIs REST e a times de Back-end Java/Spring Boot.',
      },
      {
        en: 'LGPD, data masking, role-based access control and audit logs in sensitive flows; Clean Code, SOLID, Atomic Design and unit tests with Jest.',
        pt: 'LGPD, mascaramento de dados, controle de acesso por perfil e logs de auditoria em fluxos sensíveis; Clean Code, SOLID, Atomic Design e testes unitários com Jest.',
      },
      {
        en: 'Architecture discussions, code reviews, technical refinements, CI/CD, monitoring and continuous improvement.',
        pt: 'Discussões de arquitetura, code reviews, refinamentos técnicos, CI/CD, monitoramento e melhoria contínua.',
      },
    ],
    stack: [
      'Angular',
      'TypeScript',
      'RxJS',
      'Native Federation',
      'Module Federation',
      'Jest',
      'Sass',
    ],
  },
  {
    company: 'Itaú Unibanco',
    role: { en: 'IT Engineering Analyst (Mid-level)', pt: 'Analista de Engenharia de TI (Pleno)' },
    period: { en: 'Sep 2021 – Sep 2024', pt: 'set/2021 – set/2024' },
    place: { en: 'São Paulo', pt: 'São Paulo' },
    points: [
      {
        en: 'Solutions for Itaú Empresas with Angular, TypeScript and Java, integrated with REST APIs.',
        pt: 'Soluções para o Itaú Empresas com Angular, TypeScript e Java, integradas a APIs REST.',
      },
      {
        en: 'Modernisation and optimisation of the PJ (business) debt renegotiation module, improving performance, scalability and user experience.',
        pt: 'Modernização e otimização do módulo de renegociação PJ, melhorando performance, escalabilidade e experiência do usuário.',
      },
      {
        en: 'AWS, Firebase, GA4, VWO, headless CMS and Splunk for cloud, data, experimentation and observability.',
        pt: 'AWS, Firebase, GA4, VWO, CMS Headless e Splunk em cloud, dados, experimentação e observabilidade.',
      },
      {
        en: 'Architecture decisions, code reviews, technical refinements and production changes, working with Product, Back-end, iOS and Android.',
        pt: 'Decisões de arquitetura, code reviews, refinamentos técnicos e mudanças em produção, com Produto, Back-end, iOS e Android.',
      },
    ],
    stack: ['Angular', 'TypeScript', 'Java', 'AWS', 'Firebase', 'GA4', 'VWO', 'Splunk'],
  },
  {
    company: 'everis',
    client: 'Santander',
    role: { en: 'Angular Front-end Analyst', pt: 'Analista Front End Angular' },
    period: { en: 'May 2021 – Sep 2021', pt: 'mai/2021 – set/2021' },
    place: { en: 'São Paulo', pt: 'São Paulo' },
    points: [
      {
        en: 'CACEIS investment services platform with Angular, TypeScript, JavaScript, HTML and CSS, integrated with a Java back end.',
        pt: 'Plataforma de serviços de investimento CACEIS com Angular, TypeScript, JavaScript, HTML e CSS, integrada a back-end Java.',
      },
      {
        en: 'Reactive forms, reusable components, unit tests, defect fixing and versioning with Azure DevOps.',
        pt: 'Formulários reativos, componentes reutilizáveis, testes unitários, correção de defeitos e versionamento com Azure DevOps.',
      },
    ],
    stack: ['Angular', 'TypeScript', 'Java', 'Azure DevOps'],
  },
  {
    company: 'Indra',
    client: 'Caixa Econômica Federal',
    role: { en: 'Front-end Developer', pt: 'Desenvolvedora Front-end' },
    period: { en: 'Nov 2019 – May 2021', pt: 'nov/2019 – mai/2021' },
    points: [
      {
        en: 'Migration of legacy systems to the Caixa.Negócios platform (FGTS, unemployment insurance, social cards and INSS) with Angular and Java services.',
        pt: 'Migração de sistemas legados para a plataforma Caixa.Negócios (FGTS, Seguro-Desemprego, Cartões Sociais e INSS) com Angular e serviços Java.',
      },
      {
        en: 'Dossiê Digital, responsible for digital account opening: new features, reusable screens, integrations and impact analysis.',
        pt: 'Dossiê Digital, responsável pela abertura digital de contas: novas funcionalidades, telas reutilizáveis, integrações e análise de impacto.',
      },
    ],
    stack: ['Angular', 'TypeScript', 'Java', 'GitLab'],
  },
  {
    company: 'Pier8',
    role: { en: 'Full-stack Developer', pt: 'Desenvolvedora Full Stack' },
    period: { en: 'Jun 2019 – Oct 2019', pt: 'jun/2019 – out/2019' },
    points: [
      {
        en: 'End-to-end development of a logistics platform, from responsive screens to the database structure, working directly with the client.',
        pt: 'Desenvolvimento end-to-end de plataforma logística, de telas responsivas à estrutura de banco de dados, com contato direto com o cliente.',
      },
    ],
    stack: [],
  },
  {
    company: 'Ipsos',
    role: { en: 'Junior Programmer', pt: 'Programadora Júnior' },
    period: { en: 'Jun 2018 – Dec 2018', pt: 'jun/2018 – dez/2018' },
    place: { en: 'São Paulo', pt: 'São Paulo' },
    points: [
      {
        en: 'Online market-research surveys with JavaScript, HTML and CSS; took part in an international Toyota survey run in 11 countries.',
        pt: 'Programação de pesquisas on-line com JavaScript, HTML e CSS; participação em pesquisa internacional da Toyota em 11 países.',
      },
    ],
    stack: ['JavaScript', 'HTML', 'CSS'],
  },
  {
    company: 'BITi9',
    role: { en: 'IT Infrastructure Analyst', pt: 'Analista de Infraestrutura de TI' },
    period: { en: 'May 2018', pt: 'mai/2018' },
    points: [
      {
        en: 'Rolled out OneDrive (Office 365) to 400+ machines in 20 days, ahead of the one-month deadline.',
        pt: 'Implantação do OneDrive (Office 365) em mais de 400 máquinas, concluída em 20 dias, antes do prazo de um mês.',
      },
    ],
    stack: [],
  },
];

/** Shown in one line under the experience list. */
export const EARLIER_EXPERIENCE: Text = {
  en: 'Earlier: sales consultant at Kawasaki Motores (2016–2017), where I created and ran the social media channels behind 70% of sales; sales associate at MOB (2012–2015).',
  pt: 'Experiências anteriores: consultora de vendas na Kawasaki Motores (2016–2017), criei e geri as redes sociais responsáveis por 70% das vendas; vendedora na MOB (2012–2015).',
};

export const SKILLS: { group: Text; items: string[] }[] = [
  {
    group: { en: 'Front-end', pt: 'Front-end' },
    items: [
      'Angular',
      'TypeScript',
      'JavaScript',
      'RxJS',
      'NgRx',
      'Signals',
      'HTML5',
      'CSS3 / Sass',
      'Native Federation',
      'Module Federation',
      'Atomic Design',
      'Design Systems',
    ],
  },
  {
    group: { en: 'Mobile', pt: 'Mobile' },
    items: ['React Native', 'Swift', 'SwiftUI'],
  },
  {
    group: { en: 'Applied AI', pt: 'IA aplicada' },
    items: [
      'AI agents (smolagents, LlamaIndex, LangGraph)',
      'Agentic RAG',
      'Prompt engineering',
      'AI in the development cycle',
    ],
  },
  {
    group: { en: 'Back-end', pt: 'Back-end' },
    items: ['Java', 'Spring Boot', 'REST APIs', 'PostgreSQL', 'Keycloak (OIDC)'],
  },
  {
    group: { en: 'Cloud & DevOps', pt: 'Cloud e DevOps' },
    items: [
      'AWS',
      'Azure',
      'Firebase',
      'Docker',
      'Kubernetes (k3s)',
      'Git',
      'GitHub Actions',
      'GitLab',
      'Azure DevOps',
      'CI/CD',
    ],
  },
  {
    group: { en: 'Data & observability', pt: 'Dados e observabilidade' },
    items: ['GA4', 'VWO', 'Splunk', 'Headless CMS'],
  },
  {
    group: { en: 'Quality & practices', pt: 'Qualidade e práticas' },
    items: ['Jest', 'Unit testing', 'Clean Code', 'SOLID', 'LGPD', 'Scrum / Kanban'],
  },
];

export const EDUCATION: { title: Text; detail: Text }[] = [
  {
    title: { en: 'B.Sc. Computer Engineering', pt: 'Bacharelado em Engenharia de Computação' },
    detail: {
      en: 'Universidade Paulista (UNIP) · 2014 – 2018',
      pt: 'Universidade Paulista (UNIP) · 2014 – 2018',
    },
  },
];

export const CERTIFICATIONS: Text[] = [
  {
    en: 'ACTIVATE AI Foundation Badge — Capgemini (2026)',
    pt: 'ACTIVATE AI Foundation Badge — Capgemini (2026)',
  },
  {
    en: 'Hugging Face Agents Course — Hugging Face (2026) · final project on the GAIA benchmark',
    pt: 'Hugging Face Agents Course — Hugging Face (2026) · projeto final no benchmark GAIA',
  },
  {
    en: 'AI Agents Fundamentals — Hugging Face (2026)',
    pt: 'AI Agents Fundamentals — Hugging Face (2026)',
  },
  {
    en: 'AWS SimuLearn: AI Practitioner and Cloud Practitioner — training badges, AWS (2026)',
    pt: 'AWS SimuLearn: AI Practitioner e Cloud Practitioner — Training Badges, AWS (2026)',
  },
  {
    en: 'iOS Development – Swift and SwiftUI (120 h) — ProWay / Capgemini (2026)',
    pt: 'Formação iOS Development – Swift e SwiftUI (120h) — ProWay/Capgemini (2026)',
  },
  {
    en: 'Prompt Engineering — Fundação Getulio Vargas (2026)',
    pt: 'Engenharia de Prompts — Fundação Getulio Vargas (2026)',
  },
  {
    en: 'Microsoft AZ-900: Azure Fundamentals (2024)',
    pt: 'Microsoft AZ-900: Fundamentos do Azure (2024)',
  },
  { en: 'Cybersecurity — 42 São Paulo (2024)', pt: 'Cibersegurança — 42 São Paulo (2024)' },
  {
    en: 'TypeScript Part 1: Evolving your JavaScript — Alura (2024)',
    pt: 'TypeScript Parte 1: Evoluindo seu JavaScript — Alura (2024)',
  },
];

export const LANGUAGES: Text[] = [
  { en: 'Portuguese — native', pt: 'Português — nativo' },
  {
    en: 'English — intermediate (B1–B2): good technical reading and writing; 90 hours of private lessons (Preply, 2026)',
    pt: 'Inglês — intermediário (B1–B2): boa leitura e escrita técnica; 90 horas de aulas particulares (Preply, 2026)',
  },
];

export interface ProjectCard {
  name: string;
  url: string;
  urlLabel: string;
  text: Text;
  tags: string[];
}

export const PROJECTS: ProjectCard[] = [
  {
    name: 'GAIA Agent',
    url: 'https://github.com/vanilds/gaia-agent-unit4',
    urlLabel: 'github.com/vanilds/gaia-agent-unit4',
    text: {
      en: 'AI agent for the GAIA benchmark — final project of the Hugging Face Agents Course, 60% of answers correct.',
      pt: 'Agente de IA para o benchmark GAIA — projeto final do Hugging Face Agents Course, 60% de acerto.',
    },
    tags: ['Python', 'LangGraph', 'smolagents'],
  },
  {
    name: 'My First Agent',
    url: 'https://github.com/vanilds/my-first-agent',
    urlLabel: 'github.com/vanilds/my-first-agent',
    text: {
      en: 'Agent built with smolagents, combining an LLM with web search and other tools.',
      pt: 'Agente com smolagents, combinando um LLM com busca web e outras ferramentas.',
    },
    tags: ['Python', 'smolagents'],
  },
  {
    name: 'Business by Vani Santos',
    url: 'https://businessbyvanisantos.lovable.app',
    urlLabel: 'businessbyvanisantos.lovable.app',
    text: {
      en: 'Real-estate site built with AI: first working version in minutes; 480 visitors and 539 page views in the first days, bringing new visits to a property that had been unsold for years.',
      pt: 'Site imobiliário criado com IA: primeira versão em minutos; 480 visitantes e 539 visualizações iniciais, trazendo novas visitas a um imóvel parado havia anos.',
    },
    tags: ['AI', 'Lovable', 'SEO'],
  },
];

/** LinkedIn posts (bilingual carousels). Linked to the activity page. */
export const WRITING: { title: Text; text: Text }[] = [
  {
    title: {
      en: 'Micro frontends at the bank: what nobody tells you',
      pt: 'Micro frontends no banco: o que ninguém te conta',
    },
    text: {
      en: 'Shared singletons, one login for every MFE, how micro frontends should talk, and what independent deploys really need.',
      pt: 'Singletons compartilhados, um login para todos os MFEs, como os micro frontends devem conversar e o que o deploy independente realmente exige.',
    },
  },
  {
    title: {
      en: 'What a front-end engineer does that almost nobody sees',
      pt: 'O que um front-end faz que quase ninguém vê',
    },
    text: {
      en: 'Interceptors, API gateway tests, correlation ids in Splunk / CloudWatch — and why CORS fails in the browser but not in Postman.',
      pt: 'Interceptors, testes no API Gateway, correlation-id no Splunk / CloudWatch — e por que o CORS falha no navegador e não no Postman.',
    },
  },
  {
    title: { en: 'Do Signals replace RxJS in Angular?', pt: 'Signals substituem RxJS no Angular?' },
    text: {
      en: 'UI state → Signals; asynchronous event flows → RxJS; and toSignal / toObservable when you need both.',
      pt: 'Estado da UI → Signals; fluxo de eventos assíncronos → RxJS; e toSignal / toObservable quando precisa dos dois.',
    },
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
