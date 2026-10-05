package br.com.deladopara.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private static final String KEY = "dGVzdC1vbmx5LWtleS0zMi1ieXRlcy1leGFjdGx5ISE=";

    @Test
    void encryptsWithRandomNonceAndPurposeSeparation() {
        var cipher = new SecretCipher(KEY, new SecureRandom());
        var first = cipher.encrypt("identity-verification", "opaque-token");
        var second = cipher.encrypt("identity-verification", "opaque-token");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt("identity-verification", first)).isEqualTo("opaque-token");
        assertThatThrownBy(() -> cipher.decrypt("pickup-code", first)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedKey() {
        assertThatThrownBy(() -> new SecretCipher("not-base64", new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
