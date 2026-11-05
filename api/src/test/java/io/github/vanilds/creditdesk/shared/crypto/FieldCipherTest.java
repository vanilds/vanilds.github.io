package io.github.vanilds.creditdesk.shared.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldCipherTest {

    private static final String KEY_A = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String KEY_B = Base64.getEncoder().encodeToString(filled(32, (byte) 7));

    private final FieldCipher cipher = new FieldCipher(KEY_A, KEY_B);

    @Test
    void roundTrips() {
        byte[] sealed = cipher.encrypt("52998224725", "customer.document");
        assertThat(cipher.decrypt(sealed, "customer.document")).isEqualTo("52998224725");
    }

    @Test
    void sameValueEncryptsDifferentlyEveryTime() {
        // random IV: equal CPFs are not visible as equal ciphertexts in the table
        assertThat(cipher.encrypt("52998224725", "c")).isNotEqualTo(cipher.encrypt("52998224725", "c"));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        byte[] sealed = cipher.encrypt("52998224725", "customer.document");
        sealed[sealed.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(sealed, "customer.document")).hasMessage("Decryption failed");
    }

    @Test
    void ciphertextOnlyDecryptsInItsOwnContext() {
        byte[] sealed = cipher.encrypt("52998224725", "customer.document");
        assertThatThrownBy(() -> cipher.decrypt(sealed, "customer.email")).hasMessage("Decryption failed");
    }

    @Test
    void hashIsDeterministicKeyedAndContextBound() {
        assertThat(cipher.hash("52998224725", "c")).hasSize(64).isEqualTo(cipher.hash("52998224725", "c"));
        assertThat(cipher.hash("52998224725", "c")).isNotEqualTo(cipher.hash("52998224725", "d"));
        assertThat(new FieldCipher(KEY_A, Base64.getEncoder().encodeToString(filled(32, (byte) 9)))
                .hash("52998224725", "c")).isNotEqualTo(cipher.hash("52998224725", "c"));
    }

    @Test
    void refusesMissingShortOrReusedKeys() {
        assertThatThrownBy(() -> new FieldCipher("", KEY_B)).hasMessageContaining("is not set");
        assertThatThrownBy(() -> new FieldCipher(Base64.getEncoder().encodeToString(new byte[16]), KEY_B))
                .hasMessageContaining("must be 32 bytes");
        assertThatThrownBy(() -> new FieldCipher(KEY_A, KEY_A)).hasMessageContaining("must be different");
    }

    private static byte[] filled(int size, byte value) {
        byte[] bytes = new byte[size];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}
