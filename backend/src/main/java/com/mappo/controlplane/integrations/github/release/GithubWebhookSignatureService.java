package com.mappo.controlplane.integrations.github.release;

import com.mappo.controlplane.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GithubWebhookSignatureService {

    private static final String GITHUB_SIGNATURE_PREFIX = "sha256=";

    public void verify(String rawPayload, String signatureHeader, String secret) {
        String provided = normalize(signatureHeader);
        if (!provided.startsWith(GITHUB_SIGNATURE_PREFIX)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "missing github webhook signature");
        }

        String expected = GITHUB_SIGNATURE_PREFIX + hmacSha256Hex(secret, rawPayload);
        if (!MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            provided.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid github webhook signature");
        }
    }

    private String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to verify github webhook signature");
        }
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
