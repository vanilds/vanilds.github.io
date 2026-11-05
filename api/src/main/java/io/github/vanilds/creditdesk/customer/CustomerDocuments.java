package io.github.vanilds.creditdesk.customer;

import io.github.vanilds.creditdesk.shared.crypto.FieldCipher;
import org.springframework.stereotype.Component;

/**
 * Turns a normalized CPF/CNPJ into the three columns the database keeps, and back.
 * The plain value exists only in memory, during the request that receives or reveals it.
 */
@Component
public class CustomerDocuments {

    /** Bound into the ciphertext (AES-GCM associated data) and the hash: a value only works in this column. */
    static final String CONTEXT = "customer.document";

    public record Protected(String hash, byte[] encrypted, String display) {
    }

    private final FieldCipher cipher;

    public CustomerDocuments(FieldCipher cipher) {
        this.cipher = cipher;
    }

    /** @param normalized output of {@code BrazilianDocuments.normalize}, already validated for {@code type} */
    public Protected protect(CustomerType type, String normalized) {
        return new Protected(hash(normalized), cipher.encrypt(normalized, CONTEXT), type.displayDocument(normalized));
    }

    public String hash(String normalized) {
        return normalized == null ? null : cipher.hash(normalized, CONTEXT);
    }

    String reveal(Customer customer) {
        return cipher.decrypt(customer.getDocumentEncrypted(), CONTEXT);
    }
}
