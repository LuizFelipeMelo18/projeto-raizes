package br.com.raizes.shared.infrastructure;

import br.com.raizes.identidade.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter jwt) {
        var registration = new FilterRegistrationBean<>(jwt);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwt,
                                           SecurityErrorWriter errors) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/clientes").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/unidades", "/api/v1/unidades/**",
                                "/api/v1/produtos",
                                "/swagger-ui.html", "/swagger/**", "/webjars/**", "/openapi.yaml").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/estoque", "/api/v1/estoque/movimentacoes")
                        .hasAnyRole("GERENTE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/estoque/movimentacoes")
                        .hasAnyRole("GERENTE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/auditoria",
                                "/api/v1/pedidos/relatorios/unidades-canais")
                        .hasAnyRole("GERENTE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/pedidos/acompanhar/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/pedidos")
                        .hasAnyRole("CLIENTE", "ATENDENTE", "TOTEM")
                        .requestMatchers(HttpMethod.GET, "/api/v1/pedidos")
                        .hasAnyRole("CLIENTE", "ATENDENTE", "COZINHA", "GERENTE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/pedidos/*")
                        .hasAnyRole("CLIENTE", "ATENDENTE", "COZINHA", "GERENTE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/pedidos/*/pagamentos")
                        .hasAnyRole("CLIENTE", "ATENDENTE", "TOTEM")
                        .requestMatchers(HttpMethod.POST, "/api/v1/pedidos/*/pagamentos/reconciliacao")
                        .hasAnyRole("GERENTE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/pedidos/*/cancelamento")
                        .hasAnyRole("CLIENTE", "ATENDENTE", "TOTEM")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/pedidos/*/status")
                        .hasAnyRole("COZINHA", "ATENDENTE", "GERENTE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/usuarios/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/usuarios/me/consentimentos/fidelidade")
                        .hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/fidelidade/consentimento",
                                "/api/v1/fidelidade/saldo", "/api/v1/fidelidade/movimentacoes")
                        .hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/fidelidade/consentimento")
                        .hasRole("CLIENTE")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint((request, response, ex) -> errors.unauthorized(request, response))
                        .accessDeniedHandler((request, response, ex) -> errors.forbidden(request, response)))
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
