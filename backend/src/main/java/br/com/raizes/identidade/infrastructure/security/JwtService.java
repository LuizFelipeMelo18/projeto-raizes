package br.com.raizes.identidade.infrastructure.security;

import br.com.raizes.identidade.domain.Perfil;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final String issuer;
    private final long expiresInSeconds;

    public JwtService(@Value("${raizes.security.jwt-secret-base64}") String secretBase64,
                      @Value("${raizes.security.issuer}") String issuer,
                      @Value("${raizes.security.expires-in-seconds}") long expiresInSeconds) {
        byte[] secret = Base64.getDecoder().decode(secretBase64);
        if (secret.length < 32 || expiresInSeconds < 1) {
            throw new IllegalArgumentException("JWT exige chave de ao menos 32 bytes e expiração positiva.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.issuer = issuer;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String emitir(UsuarioAutenticado usuario) {
        Instant agora = Instant.now();
        var builder = Jwts.builder().issuer(issuer).subject(usuario.id().toString())
                .issuedAt(Date.from(agora)).expiration(Date.from(agora.plusSeconds(expiresInSeconds)))
                .id(UUID.randomUUID().toString()).claim("perfil", usuario.perfil().name());
        if (usuario.unidadeId() != null) {
            builder.claim("unidadeId", usuario.unidadeId());
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    public UsuarioAutenticado verificar(String token) {
        var signed = Jwts.parser().verifyWith(key).requireIssuer(issuer).build().parseSignedClaims(token);
        if (!"HS256".equals(signed.getHeader().getAlgorithm())) {
            throw new JwtException("Algoritmo de token inválido.");
        }
        Claims claims = signed.getPayload();
        long id = Long.parseLong(claims.getSubject());
        Perfil perfil = Perfil.valueOf(claims.get("perfil", String.class));
        Object unidade = claims.get("unidadeId");
        Long unidadeId = unidade instanceof Number number ? number.longValue() : null;
        if (id < 1 || (perfil != Perfil.CLIENTE && perfil != Perfil.ADMIN && unidadeId == null)
                || ((perfil == Perfil.CLIENTE || perfil == Perfil.ADMIN) && unidadeId != null)) {
            throw new JwtException("Escopo de token inválido.");
        }
        return new UsuarioAutenticado(id, perfil, unidadeId);
    }

    public long expiresInSeconds() { return expiresInSeconds; }
}
