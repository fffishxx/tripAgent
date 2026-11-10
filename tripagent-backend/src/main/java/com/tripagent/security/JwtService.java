package com.tripagent.security;

import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.config.UserJwtProperties;
import com.tripagent.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 用户 JWT 签发与校验（RS256）。
 *
 * <p>Claims: sub=userId, type=USER, iss, aud, jti, iat, exp。
 * 不放入手机号、用户名等非必要信息。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_USER = "USER";

    private final UserJwtProperties properties;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void loadKeys() {
        try {
            privateKey = loadPrivateKey();
            publicKey = loadPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("加载用户 JWT 密钥失败，请检查 security.user-jwt 配置", e);
        }
    }

    public JwtToken generateToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + TimeUnit.HOURS.toMillis(properties.getExpireHours()));

        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_TYPE, TYPE_USER)
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .issuedAt(now)
                .expiration(expiration)
                .id(UUID.randomUUID().toString().replace("-", ""))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        return new JwtToken(token, "Bearer", TimeUnit.HOURS.toSeconds(properties.getExpireHours()));
    }

    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(properties.getIssuer())
                    .requireAudience(properties.getAudience())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TYPE_USER.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "登录凭证类型不正确");
            }
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("用户令牌校验失败：{}", e.getClass().getSimpleName());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
    }

    private PrivateKey loadPrivateKey() throws Exception {
        String pem;
        if (StringUtils.hasText(properties.getPrivateKey())) {
            pem = readPem(properties.getPrivateKey());
        } else {
            Path path = Paths.get(System.getProperty("user.home"),
                    ".tripagent", "keys", "user-jwt-private.pem");
            if (!Files.exists(path)) {
                throw new IOException("用户 JWT 私钥不存在: " + path);
            }
            pem = Files.readString(path, StandardCharsets.US_ASCII);
        }
        byte[] der = Base64.getDecoder().decode(stripPem(pem, "PRIVATE KEY"));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private PublicKey loadPublicKey() throws Exception {
        String pem;
        if (StringUtils.hasText(properties.getPublicKey())) {
            pem = readPem(properties.getPublicKey());
        } else {
            try (InputStream in = new ClassPathResource("keys/user-jwt-public.pem").getInputStream()) {
                pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
            }
        }
        byte[] der = Base64.getDecoder().decode(stripPem(pem, "PUBLIC KEY"));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private String readPem(String location) throws IOException {
        if (location.startsWith("classpath:")) {
            String classpath = location.substring("classpath:".length());
            try (InputStream in = new ClassPathResource(classpath).getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.US_ASCII);
            }
        }
        String path = location.startsWith("file:")
                ? location.substring("file:".length())
                : location;
        return Files.readString(Path.of(path), StandardCharsets.US_ASCII);
    }

    private String stripPem(String pem, String type) {
        return pem.replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
    }
}
