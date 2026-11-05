import type { Text } from '@creditdesk/shared';

/** Same rules as DocumentService.java. Checked here first so the user hears it before anything is sent. */
export const ACCEPTED_TYPES = ['application/pdf', 'image/jpeg', 'image/png'] as const;
export const MAX_FILE_SIZE = 10 * 1024 * 1024;

/** null when the file can be uploaded, otherwise the reason. */
export function checkFile(file: Pick<File, 'type' | 'size'>): Text | null {
  if (!(ACCEPTED_TYPES as readonly string[]).includes(file.type)) {
    return { en: 'Only PDF, JPEG or PNG files.', pt: 'Apenas arquivos PDF, JPEG ou PNG.' };
  }
  if (file.size === 0) {
    return { en: 'The file is empty.', pt: 'O arquivo está vazio.' };
  }
  if (file.size > MAX_FILE_SIZE) {
    return { en: 'The limit is 10 MB per file.', pt: 'O limite é 10 MB por arquivo.' };
  }
  return null;
}

export function fileSize(bytes: number, locale: string): string {
  const units = ['B', 'KB', 'MB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${new Intl.NumberFormat(locale, { maximumFractionDigits: unit ? 1 : 0 }).format(value)} ${units[unit]}`;
}
