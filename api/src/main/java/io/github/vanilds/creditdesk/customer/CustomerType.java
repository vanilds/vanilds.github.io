package io.github.vanilds.creditdesk.customer;

import io.github.vanilds.creditdesk.shared.validation.BrazilianDocuments;

/** PF = pessoa física (individual, CPF). PJ = pessoa jurídica (company, CNPJ). */
public enum CustomerType {

    PF {
        @Override
        public boolean isValidDocument(String document) {
            return BrazilianDocuments.isValidCpf(document);
        }

        /** CPF is personal data (LGPD): never returned in full by the API. */
        @Override
        public String displayDocument(String document) {
            return BrazilianDocuments.maskCpf(document);
        }

        @Override
        public String formatDocument(String document) {
            return BrazilianDocuments.formatCpf(document);
        }
    },

    PJ {
        @Override
        public boolean isValidDocument(String document) {
            return BrazilianDocuments.isValidCnpj(document);
        }

        /** CNPJ is public company data (it is on every invoice), so it is shown formatted, not masked. */
        @Override
        public String displayDocument(String document) {
            return BrazilianDocuments.formatCnpj(document);
        }

        @Override
        public String formatDocument(String document) {
            return BrazilianDocuments.formatCnpj(document);
        }
    };

    public abstract boolean isValidDocument(String document);

    /** What every screen shows (masked when it is personal data). */
    public abstract String displayDocument(String document);

    /** The full document, formatted; only for an audited reveal. */
    public abstract String formatDocument(String document);
}
