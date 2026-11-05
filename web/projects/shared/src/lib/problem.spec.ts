import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { fieldErrors, toProblem } from './problem';

describe('toProblem (RFC 9457)', () => {
  it('keeps the API problem and the correlation id', () => {
    const p = toProblem(
      new HttpErrorResponse({
        status: 400,
        headers: new HttpHeaders({ 'X-Correlation-Id': 'abc-123' }),
        error: {
          type: 'https://vanilds.github.io/problems/validation',
          title: 'Invalid request',
          status: 400,
          errors: [{ field: 'document', message: 'invalid CPF' }],
        },
      }),
    );
    expect(p.title).toBe('Invalid request');
    expect(p.correlationId).toBe('abc-123');
    expect(fieldErrors(p)).toEqual({ document: 'invalid CPF' });
    expect(p.stale).toBe(false);
  });

  it('marks 412 as stale so the screen offers a reload', () => {
    const p = toProblem(
      new HttpErrorResponse({
        status: 412,
        error: { type: 'x', title: 'Precondition failed', status: 412 },
      }),
    );
    expect(p.stale).toBe(true);
  });

  it('explains status 0 (server unreachable or CORS) instead of showing nothing', () => {
    expect(toProblem(new HttpErrorResponse({ status: 0 })).title).toContain('could not be reached');
  });
});
