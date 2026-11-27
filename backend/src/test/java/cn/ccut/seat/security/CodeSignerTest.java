package cn.ccut.seat.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 签到码的单元测试：覆盖签名、验签、篡改、过期与一次性随机数。
 * 不依赖 Spring 上下文，只用 CodeSigner 本身，保证单元层可在 CI 里秒级跑完。
 */
class CodeSignerTest {

    private final CodeSigner signer = new CodeSigner("unit-test-secret");

    @Test
    void signAndVerifyRoundTrip() {
        String token = signer.sign(11L, 21L, 15);
        CodeSigner.Payload payload = signer.verify(token);
        assertEquals(11L, payload.reservationId());
        assertEquals(21L, payload.seatId());
        assertTrue(payload.expireAt() > System.currentTimeMillis());
    }

    @Test
    void tamperedPayloadIsRejected() {
        String token = signer.sign(11L, 21L, 15);
        String signature = token.substring(token.lastIndexOf('.') + 1);
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("99:21:9999999999999:forged".getBytes(StandardCharsets.UTF_8))
                + "." + signature;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> signer.verify(forged));
        assertEquals("TOKEN_SIGNATURE_INVALID", ex.getMessage());
    }

    @Test
    void expiredTokenIsRejected() {
        String token = signer.sign(11L, 21L, -1L);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> signer.verify(token));
        assertEquals("TOKEN_EXPIRED", ex.getMessage());
    }

    @Test
    void eachTokenCarriesUniqueNonce() {
        String a = signer.sign(11L, 21L, 15);
        String b = signer.sign(11L, 21L, 15);
        assertFalse(a.equals(b));
        assertFalse(signer.verify(a).nonce().equals(signer.verify(b).nonce()));
    }

    @Test
    void malformedTokenIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> signer.verify("no-dot-token"));
        assertEquals("TOKEN_MALFORMED", ex.getMessage());
    }
}
