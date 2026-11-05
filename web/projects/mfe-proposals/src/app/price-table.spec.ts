import { lastDays, previewInstallment } from './price-table';

describe('previewInstallment (Tabela Price)', () => {
  it('matches the classic example: R$ 10,000 at 1% for 12 months', () => {
    expect(previewInstallment(10000, 0.01, 12)).toBe(888.49);
  });

  it('splits evenly with zero interest', () => {
    expect(previewInstallment(1200, 0, 12)).toBe(100);
  });

  it('returns null while the form is incomplete', () => {
    expect(previewInstallment(null, 0.02, 12)).toBeNull();
    expect(previewInstallment(1000, null, 12)).toBeNull();
  });
});

describe('lastDays', () => {
  it('uses the São Paulo date, not UTC', () => {
    // 02:30 UTC on Nov 5 is still 23:30 on Nov 4 in São Paulo
    expect(lastDays(1, new Date('2026-11-05T02:30:00Z'))).toEqual({
      from: '2026-11-04',
      to: '2026-11-04',
    });
  });

  it('covers n days including today', () => {
    expect(lastDays(30, new Date('2026-11-05T12:00:00Z'))).toEqual({
      from: '2026-10-07',
      to: '2026-11-05',
    });
  });
});
