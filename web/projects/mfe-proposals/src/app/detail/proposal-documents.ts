import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  DocumentCategory,
  I18n,
  Problem,
  ProblemBanner,
  Proposal,
  ProposalDocument,
  ProposalsApi,
  Session,
  Text,
  canEdit,
  toProblem,
} from '@creditdesk/shared';
import { switchMap } from 'rxjs';
import { ACCEPTED_TYPES, checkFile, fileSize } from './file-rules';
import { CATEGORY_LABEL } from './labels';

type Step = 'idle' | 'signing' | 'sending' | 'checking';

const STEP_LABEL: Record<Exclude<Step, 'idle'>, Text> = {
  signing: { en: 'Asking the API for an upload link…', pt: 'Pedindo à API um link de envio…' },
  sending: { en: 'Sending the file to storage…', pt: 'Enviando o arquivo ao armazenamento…' },
  checking: { en: 'The API is checking the file…', pt: 'A API está conferindo o arquivo…' },
};

/**
 * Documents of a proposal. The upload has three steps (sign → send to S3 → confirm) and the screen names each
 * one, so a failure says where it happened. Files never pass through the API.
 */
@Component({
  selector: 'cd-proposal-documents',
  imports: [FormsModule, ProblemBanner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="documents">
      <h2 class="kicker">{{ i18n.t({ en: 'Documents', pt: 'Documentos' }) }}</h2>
      <cd-problem-banner [problem]="problem()" (reload)="load()" />

      <ul class="list">
        @for (d of documents(); track d.id) {
          <li>
            <div>
              <strong>{{ d.fileName }}</strong>
              <span class="muted">
                {{ i18n.t(categories[d.category]) }} · {{ size(d.sizeBytes) }} ·
                {{ d.uploadedByName }} ·
                {{ i18n.dateTime(d.confirmedAt) }}
              </span>
            </div>
            <button type="button" class="btn btn-ghost" (click)="download(d)">
              {{ i18n.t({ en: 'Download', pt: 'Baixar' }) }}
            </button>
          </li>
        } @empty {
          <li class="muted">
            {{ i18n.t({ en: 'No documents yet.', pt: 'Nenhum documento ainda.' }) }}
          </li>
        }
      </ul>

      @if (canUpload()) {
        <form class="card upload" (ngSubmit)="upload()">
          <label class="field">
            {{ i18n.t({ en: 'Type', pt: 'Tipo' }) }}
            <select name="category" [(ngModel)]="category">
              @for (c of categoryKeys; track c) {
                <option [value]="c">{{ i18n.t(categories[c]) }}</option>
              }
            </select>
          </label>
          <label class="field">
            {{
              i18n.t({
                en: 'File (PDF, JPEG or PNG, up to 10 MB)',
                pt: 'Arquivo (PDF, JPEG ou PNG, até 10 MB)',
              })
            }}
            <input #fileInput type="file" name="file" [accept]="accept" (change)="pick($event)" />
          </label>
          @if (fileError(); as e) {
            <p class="error" role="alert">{{ i18n.t(e) }}</p>
          }
          <div class="actions">
            @if (step() !== 'idle') {
              <span class="muted" role="status">{{ i18n.t(stepLabels[busyStep()]) }}</span>
            }
            <button
              type="submit"
              class="btn btn-primary"
              [disabled]="!file() || !!fileError() || step() !== 'idle'"
            >
              {{ i18n.t({ en: 'Attach', pt: 'Anexar' }) }}
            </button>
          </div>
        </form>
      }
    </section>
  `,
  styles: `
    .documents {
      margin-top: 28px;
    }
    .list {
      list-style: none;
      padding: 0;
      margin: 12px 0 0;
      display: grid;
      gap: 8px;
    }
    .list li {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      padding: 10px 0;
      border-bottom: 1px solid var(--line-strong);
      font-size: 14px;
    }
    .list li div {
      display: grid;
      gap: 2px;
      min-width: 0;
      overflow-wrap: anywhere;
    }
    .upload {
      margin-top: 16px;
      display: grid;
      gap: 12px;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      justify-content: flex-end;
      align-items: center;
    }
    .error {
      margin: 0;
      color: var(--red);
      font-size: 14px;
    }
  `,
})
export class ProposalDocuments {
  protected readonly i18n = inject(I18n);
  private readonly session = inject(Session);
  private readonly api = inject(ProposalsApi);

  readonly proposal = input.required<Proposal>();

  protected readonly categories = CATEGORY_LABEL;
  protected readonly categoryKeys = Object.keys(CATEGORY_LABEL) as DocumentCategory[];
  protected readonly stepLabels = STEP_LABEL;
  protected readonly accept = ACCEPTED_TYPES.join(',');
  protected readonly documents = signal<ProposalDocument[]>([]);
  protected readonly problem = signal<Problem | null>(null);
  protected readonly file = signal<File | null>(null);
  protected readonly fileError = signal<Text | null>(null);
  protected readonly step = signal<Step>('idle');
  protected readonly busyStep = computed(() => {
    const s = this.step();
    return s === 'idle' ? 'signing' : s;
  });
  /** Same rule as the API: the owner, while the proposal is a DRAFT. */
  protected readonly canUpload = computed(() => canEdit(this.proposal(), this.session.user()));
  protected category: DocumentCategory = 'INCOME_PROOF';
  private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

  constructor() {
    effect(() => {
      void this.proposal().id;
      untracked(() => this.load());
    });
  }

  load(): void {
    this.problem.set(null);
    this.api.documents(this.proposal().id).subscribe({
      next: (list) => this.documents.set(list),
      error: (e) => this.problem.set(toProblem(e)),
    });
  }

  protected pick(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.file.set(file);
    this.fileError.set(file ? checkFile(file) : null);
  }

  protected upload(): void {
    const file = this.file();
    if (!file || this.fileError()) return;
    const id = this.proposal().id;
    this.problem.set(null);
    this.step.set('signing');
    this.api
      .addDocument(id, {
        category: this.category,
        fileName: file.name,
        contentType: file.type,
        sizeBytes: file.size,
      })
      .pipe(
        switchMap(({ document, upload }) => {
          this.step.set('sending');
          return this.api
            .uploadToStorage(upload, file)
            .pipe(
              switchMap(
                () => (this.step.set('checking'), this.api.confirmDocument(id, document.id)),
              ),
            );
        }),
      )
      .subscribe({
        next: () => {
          this.step.set('idle');
          this.file.set(null);
          const input = this.fileInput()?.nativeElement;
          if (input) input.value = '';
          this.load();
        },
        error: (e) => {
          this.step.set('idle');
          this.problem.set(toProblem(e));
        },
      });
  }

  protected download(d: ProposalDocument): void {
    this.api.downloadLink(this.proposal().id, d.id).subscribe({
      // The link answers with Content-Disposition: attachment, so the browser saves the file and stays here
      next: (link) => window.location.assign(link.url),
      error: (e) => this.problem.set(toProblem(e)),
    });
  }

  protected size(bytes: number): string {
    return fileSize(bytes, this.i18n.locale());
  }
}
