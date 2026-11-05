import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { CREDITDESK_ENV, isApiRequest } from './config';

/**
 * Every API call carries an `X-Correlation-Id`. The API writes it on each log line and in the audit log,
 * and returns it in error responses, so a message on screen can be matched to one request in the logs.
 */
export const correlationIdInterceptor: HttpInterceptorFn = (req, next) => {
  const env = inject(CREDITDESK_ENV, { optional: true });
  if (!env || !isApiRequest(env, req.url) || req.headers.has('X-Correlation-Id')) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'X-Correlation-Id': crypto.randomUUID() } }));
};
