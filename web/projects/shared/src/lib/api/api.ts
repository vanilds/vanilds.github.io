import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { CREDITDESK_ENV } from '../config';
import type * as Api from './schema';

/*
 * Types come from api/openapi.yaml (`npm run api:types`), the same contract the API is tested against.
 * If the API changes a field, the front stops compiling instead of breaking at runtime. CI checks the generated
 * file is up to date.
 *
 * The contract marks response fields as optional (the generator cannot know the API always fills them), so
 * responses are narrowed with Required<…>. Requests keep the contract's own required/optional fields.
 */
export type Customer = Required<Api.CustomerResponse>;
export type Proposal = Required<Api.ProposalResponse>;
export type StatusChange = Required<Api.StatusChangeResponse>;
export type ProposalStatus = Proposal['status'];
export type ProposalKind = Proposal['kind'];
export type CustomerType = Customer['type'];
export type CreateCustomer = Api.CreateCustomerRequest;
export type UpdateCustomer = Api.UpdateCustomerRequest;
export type CreateProposal = Api.CreateProposalRequest;
export type UpdateProposal = Api.UpdateProposalRequest;
export type Opinion = Required<Api.OpinionResponse>;
export type RiskFlag = Api.RiskFlag;
export type ProposalDocument = Required<Api.DocumentResponse>;
export type DocumentCategory = ProposalDocument['category'];
export type AddDocument = Api.AddDocumentRequest;
export type SignedRequest = Required<Api.SignedRequest>;
export type Download = Required<Api.DownloadResponse>;

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const PAGE_SIZES = [20, 50, 100] as const;
export type PageSize = (typeof PAGE_SIZES)[number];

/**
 * A record plus the ETag it was read with. Optimistic locking: every change sends it back in If-Match, and the
 * API answers 412 if someone else saved in the meantime — nobody silently overwrites a colleague's work.
 */
export interface Versioned<T> {
  value: T;
  etag: string;
}

export type ProposalAction =
  'submit' | 'cancel' | 'start-analysis' | 'approve' | 'reject' | 'return';

/** Workflow actions that need a reason (it goes to the status history and the audit log). */
export const REASON_REQUIRED: readonly ProposalAction[] = ['reject', 'return'];

export interface CustomerFilter {
  name?: string;
  document?: string;
  page?: number;
  size?: PageSize;
}

export interface ProposalFilter {
  status?: ProposalStatus;
  from?: string; // yyyy-MM-dd, São Paulo business date
  to?: string;
  customerId?: string;
  mine?: boolean;
  page?: number;
  size?: PageSize;
}

function params(filter: object): HttpParams {
  let p = new HttpParams();
  for (const [key, value] of Object.entries(filter)) {
    if (value !== undefined && value !== null && value !== '') {
      p = p.set(key, String(value));
    }
  }
  return p;
}

function versioned<T>(response: HttpResponse<T>): Versioned<T> {
  return { value: response.body as T, etag: response.headers.get('ETag') ?? '' };
}

function ifMatch(etag: string): HttpHeaders {
  return new HttpHeaders({ 'If-Match': etag });
}

@Injectable({ providedIn: 'root' })
export class CustomersApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(CREDITDESK_ENV).apiUrl}/api/customers`;

  search(filter: CustomerFilter = {}): Observable<Page<Customer>> {
    return this.http.get<Page<Customer>>(this.base, { params: params(filter) });
  }

  get(id: string): Observable<Versioned<Customer>> {
    return this.http
      .get<Customer>(`${this.base}/${id}`, { observe: 'response' })
      .pipe(map(versioned));
  }

  create(body: CreateCustomer): Observable<Versioned<Customer>> {
    return this.http.post<Customer>(this.base, body, { observe: 'response' }).pipe(map(versioned));
  }

  update(id: string, etag: string, body: UpdateCustomer): Observable<Versioned<Customer>> {
    return this.http
      .put<Customer>(`${this.base}/${id}`, body, { observe: 'response', headers: ifMatch(etag) })
      .pipe(map(versioned));
  }

  delete(id: string, etag: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`, { headers: ifMatch(etag) });
  }

  /** Auditor only: shows the full CPF/CNPJ once, and the reason goes to the audit log. */
  revealDocument(id: string, reason: string): Observable<string> {
    return this.http
      .post<Api.RevealedDocumentResponse>(`${this.base}/${id}/document/reveal`, { reason })
      .pipe(map((r) => r.document ?? ''));
  }
}

@Injectable({ providedIn: 'root' })
export class ProposalsApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(CREDITDESK_ENV).apiUrl}/api/proposals`;

  search(filter: ProposalFilter = {}): Observable<Page<Proposal>> {
    return this.http.get<Page<Proposal>>(this.base, { params: params(filter) });
  }

  get(id: string): Observable<Versioned<Proposal>> {
    return this.http
      .get<Proposal>(`${this.base}/${id}`, { observe: 'response' })
      .pipe(map(versioned));
  }

  history(id: string): Observable<StatusChange[]> {
    return this.http.get<StatusChange[]>(`${this.base}/${id}/history`);
  }

  create(body: CreateProposal): Observable<Versioned<Proposal>> {
    return this.http.post<Proposal>(this.base, body, { observe: 'response' }).pipe(map(versioned));
  }

  update(id: string, etag: string, body: UpdateProposal): Observable<Versioned<Proposal>> {
    return this.http
      .put<Proposal>(`${this.base}/${id}`, body, { observe: 'response', headers: ifMatch(etag) })
      .pipe(map(versioned));
  }

  /** AI opinions, newest first. Empty while the worker is still drafting (the screen polls). */
  opinions(id: string): Observable<Opinion[]> {
    return this.http.get<Opinion[]>(`${this.base}/${id}/opinions`);
  }

  documents(id: string): Observable<ProposalDocument[]> {
    return this.http.get<ProposalDocument[]>(`${this.base}/${id}/documents`);
  }

  /** Step 1 of an upload: the API checks the rules and answers with a presigned S3 URL. */
  addDocument(
    id: string,
    body: AddDocument,
  ): Observable<{ document: ProposalDocument; upload: SignedRequest }> {
    return this.http.post<{ document: ProposalDocument; upload: SignedRequest }>(
      `${this.base}/${id}/documents`,
      body,
    );
  }

  /**
   * Step 2: the file goes straight from the browser to S3, never through the API. No Authorization header here
   * (the auth interceptor only adds it to API URLs): the signature in the URL is the permission.
   */
  uploadToStorage(upload: SignedRequest, file: Blob): Observable<unknown> {
    return this.http.request(upload.method, upload.url, {
      body: file,
      headers: new HttpHeaders(upload.headers),
    });
  }

  /** Step 3: the API checks the object in S3 (size and type) and makes it available. */
  confirmDocument(id: string, documentId: string): Observable<ProposalDocument> {
    return this.http.post<ProposalDocument>(
      `${this.base}/${id}/documents/${documentId}/confirm`,
      null,
    );
  }

  /** A short-lived download link. Audited on the API side, which is why it is a POST. */
  downloadLink(id: string, documentId: string): Observable<Download> {
    return this.http.post<Download>(`${this.base}/${id}/documents/${documentId}/download`, null);
  }

  transition(
    id: string,
    etag: string,
    action: ProposalAction,
    reason?: string,
  ): Observable<Versioned<Proposal>> {
    const body =
      action === 'submit' || action === 'start-analysis' ? null : { reason: reason || undefined };
    return this.http
      .post<Proposal>(`${this.base}/${id}/${action}`, body, {
        observe: 'response',
        headers: ifMatch(etag),
      })
      .pipe(map(versioned));
  }
}
