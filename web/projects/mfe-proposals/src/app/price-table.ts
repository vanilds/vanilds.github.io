/**
 * Tabela Price (French amortisation): fixed installment PMT = PV · i / (1 − (1 + i)^−n).
 * Only a preview while typing — the API computes the official value with BigDecimal and HALF_UP rounding to cents
 * (api/…/PriceTable.java). Money is never decided by a JavaScript float.
 */
export function previewInstallment(
  amount: number | null,
  monthlyRate: number | null,
  installments: number | null,
): number | null {
  if (
    !amount ||
    !installments ||
    monthlyRate === null ||
    monthlyRate < 0 ||
    amount <= 0 ||
    installments <= 0
  ) {
    return null;
  }
  if (monthlyRate === 0) {
    return round2(amount / installments);
  }
  return round2((amount * monthlyRate) / (1 - Math.pow(1 + monthlyRate, -installments)));
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

/** Last n days as São Paulo business dates (yyyy-MM-dd), the format the API filter expects. */
export function lastDays(n: number, now = new Date()): { from: string; to: string } {
  const fmt = (d: Date) =>
    new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(d);
  const from = new Date(now.getTime() - (n - 1) * 24 * 60 * 60 * 1000);
  return { from: fmt(from), to: fmt(now) };
}
