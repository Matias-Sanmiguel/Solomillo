package dev.solomillo.core;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final AppProperties props;

    public JwtUtil(AppProperties props) {
        this.props = props;
    }

    public String createToken(String email, String rol) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claim("rol", rol)
                .issuedAt(new Date(now))
                .expiration(new Date(now + props.jwtExpirationMs))
                .signWith(key())
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(props.jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
