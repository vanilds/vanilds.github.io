import { HttpErrorResponse } from '@angular/common/http';

export interface FieldViolation {
  field: string;
  message: string;
}

/** RFC 9457 "problem details" — the one error format the API uses for every failure. */
export interface Problem {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  errors?: FieldViolation[];
  /** From the X-Correlation-Id response header: what to search for in the logs. */
  correlationId?: string;
  /** The screen should reload the record before trying again (412: someone else changed it). */
  stale: boolean;
}

export function toProblem(error: unknown): Problem {
  if (!(error instanceof HttpErrorResponse)) {
    return {
      type: 'about:blank',
      title: 'Unexpected error',
      status: 0,
      detail: String(error),
      stale: false,
    };
  }
  const correlationId = error.headers?.get('X-Correlation-Id') ?? undefined;
  if (error.status === 0) {
    // CORS refusal, DNS failure or the cluster is off: the browser hides which one on purpose
    return {
      type: 'about:blank',
      title: 'The server could not be reached',
      status: 0,
      detail: 'The demo may be offline (it runs on weekdays, 08:00–20:00 São Paulo time).',
      stale: false,
    };
  }
  const body = error.error;
  if (body && typeof body === 'object' && 'title' in body) {
    return {
      ...(body as Omit<Problem, 'stale' | 'correlationId'>),
      correlationId,
      stale: error.status === 412,
    };
  }
  return {
    type: 'about:blank',
    title: error.statusText || 'Error',
    status: error.status,
    correlationId,
    stale: error.status === 412,
  };
}

/** Field errors indexed by field name, ready to show under each form input. */
export function fieldErrors(problem: Problem | null): Record<string, string> {
  return Object.fromEntries((problem?.errors ?? []).map((e) => [e.field, e.message]));
}
