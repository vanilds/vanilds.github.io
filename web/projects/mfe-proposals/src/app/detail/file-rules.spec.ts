import { checkFile, fileSize, MAX_FILE_SIZE } from './file-rules';

describe('file rules', () => {
  it('accepts PDF, JPEG and PNG up to 10 MB', () => {
    expect(checkFile({ type: 'application/pdf', size: 1000 })).toBeNull();
    expect(checkFile({ type: 'image/jpeg', size: MAX_FILE_SIZE })).toBeNull();
    expect(checkFile({ type: 'image/png', size: 1 })).toBeNull();
  });

  it('refuses other types, empty and too large files', () => {
    expect(checkFile({ type: 'text/html', size: 10 })?.en).toContain('Only PDF');
    expect(checkFile({ type: 'image/svg+xml', size: 10 })).not.toBeNull();
    expect(checkFile({ type: 'application/pdf', size: 0 })?.pt).toContain('vazio');
    expect(checkFile({ type: 'application/pdf', size: MAX_FILE_SIZE + 1 })?.en).toContain('10 MB');
  });

  it('formats sizes for people', () => {
    expect(fileSize(512, 'en-US')).toBe('512 B');
    expect(fileSize(184_320, 'en-US')).toBe('180 KB');
    expect(fileSize(2_621_440, 'pt-BR')).toBe('2,5 MB');
  });
});
