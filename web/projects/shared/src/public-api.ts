/*
 * @creditdesk/shared — code shared by the shell and the micro frontends.
 * Native Federation loads it once (singleton), so Session and I18n are the same instances in every app on screen.
 */
export * from './lib/config';
export * from './lib/auth';
export * from './lib/correlation-id';
export * from './lib/i18n';
export * from './lib/problem';
export * from './lib/workflow';
export * from './lib/api/api';
export * from './lib/ui/status-badge';
export * from './lib/ui/problem-banner';
export * from './lib/ui/pager';
