import { Injectable, computed, effect, signal } from '@angular/core';

export type Lang = 'en' | 'pt';
export type Text = Record<Lang, string>;

const STORAGE_KEY = 'creditdesk.lang';

function initialLang(): Lang {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'en' || saved === 'pt') return saved;
  } catch {
    // storage blocked (private window): fall back to the browser language
  }
  return navigator.language?.toLowerCase().startsWith('pt') ? 'pt' : 'en';
}

/**
 * Two languages without a build per locale: texts live next to the component as { en, pt } pairs and the
 * language is a signal. Shared as a singleton through Native Federation, so switching in the shell also
 * switches every micro frontend on screen.
 */
@Injectable({ providedIn: 'root' })
export class I18n {
  readonly lang = signal<Lang>(initialLang());
  readonly locale = computed(() => (this.lang() === 'pt' ? 'pt-BR' : 'en-US'));

  constructor() {
    effect(() => {
      const lang = this.lang();
      document.documentElement.lang = lang === 'pt' ? 'pt-BR' : 'en';
      try {
        localStorage.setItem(STORAGE_KEY, lang);
      } catch {
        /* ignore */
      }
    });
  }

  t(text: Text): string {
    return text[this.lang()];
  }

  toggle(): void {
    this.lang.update((l) => (l === 'en' ? 'pt' : 'en'));
  }

  /** Amounts are always in reais, whatever the interface language. */
  money(value: number | null | undefined): string {
    if (value === null || value === undefined) return '—';
    return new Intl.NumberFormat(this.locale(), { style: 'currency', currency: 'BRL' }).format(
      value,
    );
  }

  percent(monthlyRate: number | null | undefined): string {
    if (monthlyRate === null || monthlyRate === undefined) return '—';
    return new Intl.NumberFormat(this.locale(), {
      style: 'percent',
      minimumFractionDigits: 2,
    }).format(monthlyRate);
  }

  dateTime(iso: string | null | undefined): string {
    if (!iso) return '—';
    return new Intl.DateTimeFormat(this.locale(), {
      dateStyle: 'short',
      timeStyle: 'short',
      timeZone: 'America/Sao_Paulo',
    }).format(new Date(iso));
  }
}
