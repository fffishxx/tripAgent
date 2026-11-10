package com.tripagent.security;

import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.config.UserJwtProperties;
import com.tripagent.user.entity.User;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class JwtServiceTest {

    @TempDir
    Path tempDir;

    private KeyPair keyPair;
    private UserJwtProperties properties;
    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        Path privatePath = tempDir.resolve("private.pem");
        Path publicPath = tempDir.resolve("public.pem");
        Files.writeString(privatePath, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()),
                StandardCharsets.US_ASCII);
        Files.writeString(publicPath, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()),
                StandardCharsets.US_ASCII);

        properties = new UserJwtProperties();
        properties.setPrivateKey(privatePath.toString());
        properties.setPublicKey(publicPath.toString());
        properties.setIssuer("test-issuer");
        properties.setAudience("test-audience");
        properties.setExpireHours(1);

        jwtService = new JwtService(properties);
        jwtService.loadKeys();
    }

    @Test
    void generateAndParseValidToken() {
        User user = new User();
        user.setId(123L);

        JwtToken token = jwtService.generateToken(user);

        assertThat(token.accessToken()).isNotBlank();
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(3600);
        assertThat(jwtService.parseUserId(token.accessToken())).isEqualTo(123L);
    }

    @Test
    void expiredTokenIsRejected() {
        UserJwtProperties expiredProperties = new UserJwtProperties();
        expiredProperties.setPrivateKey(properties.getPrivateKey());
        expiredProperties.setPublicKey(properties.getPublicKey());
        expiredProperties.setIssuer(properties.getIssuer());
        expiredProperties.setAudience(properties.getAudience());
        expiredProperties.setExpireHours(-1);

        JwtService expiredService = new JwtService(expiredProperties);
        expiredService.loadKeys();
        User user = new User();
        user.setId(1L);
        String token = expiredService.generateToken(user).accessToken();

        assertUnauthorized(() -> jwtService.parseUserId(token));
    }

    @Test
    void wrongTypeIsRejected() {
        String token = customToken("USER_TOKEN_SUBJECT", "APP", "test-issuer", "test-audience");

        assertUnauthorized(() -> jwtService.parseUserId(token));
    }

    @Test
    void wrongIssuerIsRejected() {
        String token = customToken("1", "USER", "other-issuer", "test-audience");

        assertUnauthorized(() -> jwtService.parseUserId(token));
    }

    @Test
    void wrongAudienceIsRejected() {
        String token = customToken("1", "USER", "test-issuer", "other-audience");

        assertUnauthorized(() -> jwtService.parseUserId(token));
    }

    @Test
    void nonNumericSubjectIsRejected() {
        String token = customToken("not-a-number", "USER", "test-issuer", "test-audience");

        assertUnauthorized(() -> jwtService.parseUserId(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        User user = new User();
        user.setId(1L);
        String token = jwtService.generateToken(user).accessToken();

        assertUnauthorized(() -> jwtService.parseUserId(token + "tampered"));
    }

    private String customToken(String subject, String type, String issuer, String audience) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim("type", type)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private void assertUnauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        BusinessException ex = catchThrowableOfType(callable, BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getResultCode()).isEqualTo(ResultCode.UNAUTHORIZED);
    }

    private String pem(String type, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }
}
