package customer.identityservice.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Phát hành access token JWT ký RS256.
 *
 * <p>Bản tạm để chạy end-to-end: không thêm thư viện JWT, chỉ dùng JDK
 * (SHA256withRSA) để gateway hiện có xác thực được bằng public key.</p>
 */
@Service
public class JwtService {
    private final ObjectMapper mapper;
    private final PrivateKey privateKey;
    private final String issuer;
    private final String audience;
    private final long accessTtlSeconds;

    public JwtService(ObjectMapper mapper,
            @Value("${security.jwt.private-key:}") Resource privateKeyResource,
            @Value("${security.jwt.issuer:library-identity}") String issuer,
            @Value("${security.jwt.audience:library-api}") String audience,
            @Value("${security.jwt.access-ttl-seconds:3600}") long accessTtlSeconds) throws Exception {
        this.mapper = mapper;
        this.issuer = issuer;
        this.audience = audience;
        this.accessTtlSeconds = accessTtlSeconds;
        this.privateKey = loadPrivateKey(privateKeyResource);
    }

    public String issue(String subject, List<String> roles) {
        try {
            long now = Instant.now().getEpochSecond();
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "RS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", subject);
            payload.put("iss", issuer);
            payload.put("aud", audience);
            payload.put("roles", roles);
            payload.put("iat", now);
            payload.put("nbf", now);
            payload.put("exp", now + accessTtlSeconds);

            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String signingInput = encoder.encodeToString(mapper.writeValueAsBytes(header))
                    + "." + encoder.encodeToString(mapper.writeValueAsBytes(payload));

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + encoder.encodeToString(signature.sign());
        } catch (Exception exception) {
            throw new IllegalStateException("Không tạo được access token", exception);
        }
    }

    private static PrivateKey loadPrivateKey(Resource resource) throws Exception {
        if (resource == null || !resource.exists()) {
            throw new IllegalStateException("Thiếu security.jwt.private-key (file PEM)");
        }
        String pem = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8)
                .replaceAll("-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
