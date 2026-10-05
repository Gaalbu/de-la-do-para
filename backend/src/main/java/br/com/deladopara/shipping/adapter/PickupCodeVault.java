package br.com.deladopara.shipping.adapter;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Encrypts the pickup code at rest with a purpose-separated AES-GCM key. */
@Component
public class PickupCodeVault {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String PURPOSE = "pickup-code/v1";

    private final byte[] masterKey;
    private final SecureRandom random;

    public PickupCodeVault(@Value("${app.data-encryption-key}") String encodedKey, SecureRandom random) {
        try {
            masterKey = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("APP_DATA_ENCRYPTION_KEY must be Base64", e);
        }
        if (masterKey.length != 32) {
            throw new IllegalArgumentException("APP_DATA_ENCRYPTION_KEY must decode to exactly 32 bytes");
        }
        this.random = random;
    }

    public String encrypt(String code) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(PURPOSE.getBytes(StandardCharsets.UTF_8));
            var encrypted = cipher.doFinal(code.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                            .put(nonce)
                            .put(encrypted)
                            .array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt pickup code", e);
        }
    }

    public String decrypt(String envelope) {
        try {
            byte[] bytes = Base64.getDecoder().decode(envelope);
            if (bytes.length <= NONCE_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted pickup code");
            }
            var buffer = ByteBuffer.wrap(bytes);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(PURPOSE.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt pickup code", e);
        }
    }

    private SecretKeySpec key() throws GeneralSecurityException {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
        return new SecretKeySpec(
                mac.doFinal(("de-la-do-para/data-key/v1/" + PURPOSE).getBytes(StandardCharsets.UTF_8)), "AES");
    }
}
