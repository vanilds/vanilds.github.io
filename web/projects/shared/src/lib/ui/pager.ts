import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { PAGE_SIZES, PageSize } from '../api/api';
import { I18n } from '../i18n';

@Component({
  selector: 'cd-pager',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="pager" [attr.aria-label]="i18n.t({ en: 'Pagination', pt: 'Paginação' })">
      <span class="muted">
        {{ total() }} {{ i18n.t({ en: 'records', pt: 'registros' }) }} ·
        {{ i18n.t({ en: 'page', pt: 'página' }) }} {{ page() + 1 }} / {{ pages() || 1 }}
      </span>
      <label class="size">
        <span>{{ i18n.t({ en: 'Rows', pt: 'Linhas' }) }}</span>
        <select [value]="size()" (change)="sizeChange.emit($any(+$any($event.target).value))">
          @for (s of sizes; track s) {
            <option [value]="s">{{ s }}</option>
          }
        </select>
      </label>
      <button
        type="button"
        class="btn"
        [disabled]="page() === 0"
        (click)="pageChange.emit(page() - 1)"
      >
        ‹
      </button>
      <button
        type="button"
        class="btn"
        [disabled]="page() + 1 >= pages()"
        (click)="pageChange.emit(page() + 1)"
      >
        ›
      </button>
    </nav>
  `,
  styles: `
    .pager {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      align-items: center;
      justify-content: flex-end;
      margin-top: 12px;
      font-size: 13px;
    }
    .pager .muted {
      margin-right: auto;
    }
    .size {
      display: flex;
      gap: 8px;
      align-items: center;
      color: var(--muted);
    }
    .size select {
      width: auto;
    }
  `,
})
export class Pager {
  protected readonly i18n = inject(I18n);
  protected readonly sizes = PAGE_SIZES;
  readonly page = input.required<number>();
  readonly pages = input.required<number>();
  readonly total = input.required<number>();
  readonly size = input.required<PageSize>();
  readonly pageChange = output<number>();
  readonly sizeChange = output<PageSize>();
}
