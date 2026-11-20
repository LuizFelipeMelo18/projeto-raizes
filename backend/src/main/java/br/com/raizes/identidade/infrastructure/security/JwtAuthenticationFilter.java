package br.com.raizes.identidade.infrastructure.security;

import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.identidade.infrastructure.jpa.UsuarioJpaRepository;
import br.com.raizes.shared.infrastructure.SecurityErrorWriter;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UsuarioJpaRepository usuarios;
    private final SecurityErrorWriter errors;

    public JwtAuthenticationFilter(JwtService jwt, UsuarioJpaRepository usuarios, SecurityErrorWriter errors) {
        this.jwt = jwt;
        this.usuarios = usuarios;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!header.startsWith("Bearer ") || header.length() > 8192) {
            errors.unauthorized(request, response);
            return;
        }
        UsuarioAutenticado principal;
        try {
            principal = jwt.verificar(header.substring(7));
        } catch (JwtException | IllegalArgumentException ex) {
            errors.unauthorized(request, response);
            return;
        }
        var user = usuarios.findById(principal.id()).orElse(null);
        if (user == null || !user.isAtivo() || user.getPerfil() != principal.perfil()
                || !Objects.equals(user.getUnidadeId(), principal.unidadeId())) {
            errors.unauthorized(request, response);
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_" + principal.perfil().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
