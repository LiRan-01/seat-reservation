package cn.ccut.seat.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 签到码：带签名的结构化令牌，服务端无需落库即可自证身份。
 * 结构：base64url(reservationId:seatId:expireEpoch:nonce).hmacSha256Hex
 */
@Component
public class CodeSigner {

    private final String secret;

    public CodeSigner(@Value("${app.secret}") String secret) {
        this.secret = secret;
    }

    public record Payload(Long reservationId, Long seatId, long expireAt, String nonce) {
    }

    public String sign(Long reservationId, Long seatId, long validMinutes) {
        long exp = Instant.now().plusSeconds(validMinutes * 60).toEpochMilli();
        String payload = reservationId + ":" + seatId + ":" + exp + ":" + UUID.randomUUID();
        String b64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return b64 + "." + hmac(b64);
    }

    public Payload verify(String token) {
        int dot = token.lastIndexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            throw new IllegalArgumentException("TOKEN_MALFORMED");
        }
        String b64 = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        if (!hmac(b64).equals(sig)) {
            throw new IllegalArgumentException("TOKEN_SIGNATURE_INVALID");
        }
        String payload = new String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8);
        String[] parts = payload.split(":");
        if (parts.length != 4) {
            throw new IllegalArgumentException("TOKEN_PAYLOAD_INVALID");
        }
        long exp = Long.parseLong(parts[2]);
        if (Instant.now().toEpochMilli() > exp) {
            throw new IllegalArgumentException("TOKEN_EXPIRED");
        }
        return new Payload(Long.parseLong(parts[0]), Long.parseLong(parts[1]), exp, parts[3]);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC init failed", e);
        }
    }
}
