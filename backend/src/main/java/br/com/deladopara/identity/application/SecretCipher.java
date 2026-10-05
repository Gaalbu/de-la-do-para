package br.com.deladopara.identity.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-GCM envelope encryption with key separation by purpose. */
public final class SecretCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final byte[] masterKey;
    private final SecureRandom random;

    public SecretCipher(String encodedKey, SecureRandom random) {
        try {
            this.masterKey = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("APP_DATA_ENCRYPTION_KEY must be Base64", e);
        }
        if (masterKey.length != 32) {
            throw new IllegalArgumentException("APP_DATA_ENCRYPTION_KEY must decode to exactly 32 bytes");
        }
        this.random = random;
    }

    public String encrypt(String purpose, String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keyFor(purpose), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            var encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                            .put(nonce)
                            .put(encrypted)
                            .array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt secret", e);
        }
    }

    public String decrypt(String purpose, String envelope) {
        try {
            byte[] bytes = Base64.getDecoder().decode(envelope);
            if (bytes.length <= NONCE_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted secret");
            }
            var buffer = ByteBuffer.wrap(bytes);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keyFor(purpose), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt secret", e);
        }
    }

    private SecretKeySpec keyFor(String purpose) throws GeneralSecurityException {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
        var context = "de-la-do-para/data-key/v1/" + purpose;
        return new SecretKeySpec(mac.doFinal(context.getBytes(StandardCharsets.UTF_8)), "AES");
    }
}
