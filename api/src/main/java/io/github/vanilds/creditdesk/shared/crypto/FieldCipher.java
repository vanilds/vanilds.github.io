package io.github.vanilds.creditdesk.shared.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Application-level encryption for a few sensitive columns (today: the customer's CPF/CNPJ).
 *
 * <p><b>Why not only disk encryption?</b> Disk/RDS encryption protects a stolen disk, but anyone who can run
 * {@code SELECT} — a leaked DB password, a backup copied to the wrong bucket, a support query — sees the CPF in
 * clear. Here the database only ever stores ciphertext; the key lives outside it (env var → Kubernetes Secret →
 * AWS SSM in Phase 6).
 *
 * <p><b>Two keys, two jobs:</b>
 * <ul>
 *   <li><b>AES-256-GCM</b> (random 96-bit IV per value) to store and later read the value. GCM also authenticates:
 *   a tampered ciphertext fails to decrypt instead of returning garbage. The column name is bound as
 *   "associated data", so a ciphertext copied into another column will not decrypt there either.</li>
 *   <li><b>HMAC-SHA256</b> with a separate key for lookups. A random IV means the same CPF encrypts differently each
 *   time, so you cannot {@code WHERE document = ?} or put a unique index on it. The HMAC is deterministic: same CPF,
 *   same hash, so search and the unique index work on the hash. A plain SHA-256 would not do: there are only
 *   ~10⁹ CPFs, an attacker could hash them all in minutes. Without the HMAC key, the hash is useless.</li>
 * </ul>
 *
 * <p>Stored layout: {@code [1 byte key version][12 bytes IV][ciphertext + 16 bytes tag]}. The version byte is what
 * makes key rotation possible later: new writes use the new key, old rows still say which key decrypts them.
 */
@Component
public class FieldCipher {

    private static final byte KEY_VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec hashKey;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(@Value("${creditdesk.crypto.encryption-key}") String encryptionKeyBase64,
                       @Value("${creditdesk.crypto.hash-key}") String hashKeyBase64) {
        byte[] encryption = decodeKey("creditdesk.crypto.encryption-key", encryptionKeyBase64);
        byte[] hash = decodeKey("creditdesk.crypto.hash-key", hashKeyBase64);
        if (Arrays.equals(encryption, hash)) {
            throw new IllegalStateException("Encryption and hash keys must be different");
        }
        this.encryptionKey = new SecretKeySpec(encryption, "AES");
        this.hashKey = new SecretKeySpec(hash, "HmacSHA256");
    }

    /** @param context where the value lives, e.g. "customer.document" — must be the same to decrypt */
    public byte[] encrypt(String plaintext, String context) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + IV_BYTES + sealed.length).put(KEY_VERSION).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(byte[] stored, String context) {
        if (stored == null || stored.length < 1 + IV_BYTES + TAG_BITS / 8 || stored[0] != KEY_VERSION) {
            throw new IllegalStateException("Unknown ciphertext format or key version");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, stored, 1, IV_BYTES));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(stored, 1 + IV_BYTES, stored.length - 1 - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Wrong key, wrong context or tampered bytes: never return a guess
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    /** Deterministic keyed hash (64 hex chars) for equality search and unique indexes. */
    public String hash(String plaintext, String context) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hashKey);
            mac.update(context.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0); // separator: context "ab"+"c" must differ from "a"+"bc"
            return HexFormat.of().formatHex(mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Hashing failed", e);
        }
    }

    private static byte[] decodeKey(String property, String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(property + " is not set (32 random bytes, base64: openssl rand -base64 32)");
        }
        byte[] key = Base64.getDecoder().decode(base64.trim());
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException(property + " must be 32 bytes (256 bits); got " + key.length);
        }
        return key;
    }
}
