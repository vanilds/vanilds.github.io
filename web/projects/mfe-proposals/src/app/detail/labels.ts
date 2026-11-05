import type { DocumentCategory, Opinion, Text } from '@creditdesk/shared';

export const RECOMMENDATION_LABEL: Record<Opinion['recommendation'], Text> = {
  FAVORABLE: { en: 'Favorable', pt: 'Favorável' },
  CAUTION: { en: 'Favorable with caution', pt: 'Favorável com ressalvas' },
  UNFAVORABLE: { en: 'Unfavorable', pt: 'Desfavorável' },
};

export const RISK_LABEL: Record<Opinion['riskLevel'], Text> = {
  LOW: { en: 'low risk', pt: 'risco baixo' },
  MEDIUM: { en: 'medium risk', pt: 'risco médio' },
  HIGH: { en: 'high risk', pt: 'risco alto' },
};

/** Flags are translated by code; an unknown code (a newer worker) falls back to the worker's English message. */
export const FLAG_LABEL: Record<string, Text> = {
  HIGH_INSTALLMENT_TO_INCOME: {
    en: 'Installment above the income limit',
    pt: 'Parcela acima do limite da renda',
  },
  LARGE_AMOUNT: { en: 'Amount high for the income', pt: 'Valor alto para a renda' },
  MISSING_INCOME_PROOF: { en: 'No proof of income', pt: 'Sem comprovante de renda' },
  MISSING_DEBT_STATEMENT: { en: 'No statement of the debt', pt: 'Sem extrato da dívida' },
  RECENT_REJECTIONS: {
    en: 'Rejections in the last 12 months',
    pt: 'Reprovações nos últimos 12 meses',
  },
  CONCURRENT_PROPOSALS: { en: 'Other proposals in progress', pt: 'Outras propostas em andamento' },
  NEW_CUSTOMER: { en: 'Customer for less than 6 months', pt: 'Cliente há menos de 6 meses' },
};

export const CATEGORY_LABEL: Record<DocumentCategory, Text> = {
  INCOME_PROOF: { en: 'Proof of income', pt: 'Comprovante de renda' },
  FINANCIAL_STATEMENT: { en: 'Financial statement (company)', pt: 'Demonstrativo financeiro (PJ)' },
  BANK_STATEMENT: { en: 'Bank statement', pt: 'Extrato bancário' },
  IDENTITY: { en: 'Identity document', pt: 'Documento de identidade' },
  ADDRESS_PROOF: { en: 'Proof of address', pt: 'Comprovante de endereço' },
  DEBT_STATEMENT: { en: 'Statement of the debt', pt: 'Extrato da dívida' },
  OTHER: { en: 'Other', pt: 'Outro' },
};
