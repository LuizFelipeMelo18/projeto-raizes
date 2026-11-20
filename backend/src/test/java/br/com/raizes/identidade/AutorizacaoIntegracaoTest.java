package br.com.raizes.identidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AutorizacaoIntegracaoTest {
    @Value("${local.server.port}")
    int port;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    PasswordEncoder passwords;

    private final HttpClient http = HttpClient.newHttpClient();
    private Long novoUsuarioId;

    @AfterEach
    void limparFixtures() {
        jdbc.update("DELETE FROM auditoria WHERE pedido_id IN (9101, 9102)");
        jdbc.update("DELETE FROM item_pedido WHERE pedido_id IN (9101, 9102)");
        jdbc.update("DELETE FROM pagamento WHERE pedido_id IN (9101, 9102)");
        jdbc.update("DELETE FROM pedido WHERE id IN (9101, 9102)");
        if (novoUsuarioId != null) {
            jdbc.update("DELETE FROM auditoria WHERE ator_id = ? OR sujeito_usuario_id = ?", novoUsuarioId, novoUsuarioId);
            jdbc.update("DELETE FROM consentimento WHERE usuario_id = ?", novoUsuarioId);
            jdbc.update("DELETE FROM usuario WHERE id = ?", novoUsuarioId);
            novoUsuarioId = null;
        }
    }

    @Test
    void cadastroConsentimentoELoginNaoExibemSenha() throws Exception {
        String email = "teste." + UUID.randomUUID() + "@raizes.invalid";
        String senha = "SenhaForte@123";
        var cadastro = send("POST", "/api/v1/clientes", """
                {"nome":"Cliente Teste","email":"%s","senha":"%s","consentimentoFidelidade":true,"perfil":"ADMIN","unidadeId":2}
                """.formatted(email, senha), null);
        assertEquals(201, cadastro.statusCode(), cadastro.body());
        JsonNode criado = json(cadastro);
        novoUsuarioId = criado.path("id").asLong();
        assertEquals("CLIENTE", criado.path("perfil").asText());
        assertTrue(jdbc.queryForObject("SELECT unidade_id IS NULL FROM usuario WHERE id = ?", Boolean.class,
                novoUsuarioId));
        assertTrue(criado.path("consentimentoFidelidade").asBoolean());
        assertFalse(cadastro.body().contains(senha));
        assertFalse(cadastro.body().contains("senhaHash"));
        String hash = jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE id = ?", String.class, novoUsuarioId);
        assertNotEquals(senha, hash);
        assertTrue(passwords.matches(senha, hash));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM consentimento WHERE usuario_id = ?", Integer.class,
                novoUsuarioId));

        var login = send("POST", "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}", null);
        assertEquals(200, login.statusCode(), login.body());
        String token = json(login).path("accessToken").asText();
        assertEquals(3, token.split("\\.").length);
        assertFalse(login.body().contains(senha));
        var me = send("GET", "/api/v1/usuarios/me", null, token);
        assertEquals(200, me.statusCode(), me.body());
        assertEquals(email, json(me).path("email").asText());

        var revogacao = send("PUT", "/api/v1/usuarios/me/consentimentos/fidelidade",
                "{\"consentido\":false}", token);
        assertEquals(200, revogacao.statusCode(), revogacao.body());
        assertFalse(json(revogacao).path("consentido").asBoolean());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM consentimento WHERE usuario_id = ?", Integer.class,
                novoUsuarioId));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE sujeito_usuario_id = ? AND acao = 'ACESSO_DADO_PESSOAL'",
                Integer.class, novoUsuarioId) >= 1);
    }

    @Test
    void credenciaisInvalidasTokenAusenteEAlteradoRetornam401() throws Exception {
        var invalido = send("POST", "/api/v1/auth/login",
                "{\"email\":\"cliente@raizes.invalid\",\"senha\":\"errada\"}", null);
        assertError(invalido, 401, "CREDENCIAIS_INVALIDAS");
        var ausente = send("GET", "/api/v1/usuarios/me", null, null);
        assertError(ausente, 401, "NAO_AUTENTICADO");
        String token = token("cliente@raizes.invalid");
        var alterado = send("GET", "/api/v1/usuarios/me", null, token.substring(0, token.length() - 2) + "xx");
        assertError(alterado, 401, "NAO_AUTENTICADO");
        var campo = send("POST", "/api/v1/auth/login", "{\"email\":\"invalido\",\"senha\":\"\"}", null);
        assertError(campo, 422, "VALIDACAO_INVALIDA");
    }

    @Test
    void tokenPerdeAcessoSeUsuarioForDesativadoOuMudarDePerfil() throws Exception {
        String token = token("atendente@raizes.invalid");
        try {
            jdbc.update("UPDATE usuario SET ativo = FALSE WHERE id = 102");
            assertError(send("GET", "/api/v1/usuarios/me", null, token), 401, "NAO_AUTENTICADO");
            jdbc.update("UPDATE usuario SET ativo = TRUE, perfil = 'GERENTE' WHERE id = 102");
            assertError(send("GET", "/api/v1/usuarios/me", null, token), 401, "NAO_AUTENTICADO");
            jdbc.update("UPDATE usuario SET perfil = 'ATENDENTE', unidade_id = 2 WHERE id = 102");
            assertError(send("GET", "/api/v1/usuarios/me", null, token), 401, "NAO_AUTENTICADO");
        } finally {
            jdbc.update("UPDATE usuario SET ativo = TRUE, perfil = 'ATENDENTE', unidade_id = 1 WHERE id = 102");
        }
    }

    @Test
    void pedidoExigePropriedadePerfilEUnidadeCorretos() throws Exception {
        jdbc.update("""
                INSERT INTO pedido (id, unidade_id, cliente_id, criado_por_id, canal_pedido,
                modalidade_atendimento, status, forma_pagamento, total)
                VALUES (9101, 1, 100, 100, 'APP', 'RETIRADA', 'AGUARDANDO_PAGAMENTO', 'MOCK', 39.90),
                       (9102, 2, 108, 108, 'APP', 'RETIRADA', 'AGUARDANDO_PAGAMENTO', 'MOCK', 42.00)
                """);
        jdbc.update("""
                INSERT INTO item_pedido (pedido_id, produto_id, quantidade, preco_unitario, subtotal)
                VALUES (9101, 10, 1, 39.90, 39.90), (9102, 10, 1, 42.00, 42.00)
                """);
        assertEquals(200, send("GET", "/api/v1/pedidos/9101", null, token("cliente@raizes.invalid")).statusCode());
        assertError(send("GET", "/api/v1/pedidos/9101", null, token("cliente2@raizes.invalid")),
                404, "PEDIDO_NAO_ENCONTRADO");
        assertEquals(200, send("GET", "/api/v1/pedidos/9101", null, token("atendente@raizes.invalid")).statusCode());
        assertError(send("GET", "/api/v1/pedidos/9101", null, token("atendente.orla@raizes.invalid")),
                403, "ACESSO_NEGADO");
        assertEquals(200, send("GET", "/api/v1/pedidos/9102", null, token("atendente.orla@raizes.invalid")).statusCode());
        assertEquals(200, send("GET", "/api/v1/pedidos/9101", null, token("cozinha@raizes.invalid")).statusCode());
        assertEquals(200, send("GET", "/api/v1/pedidos/9101", null, token("gerente@raizes.invalid")).statusCode());
        assertEquals(200, send("GET", "/api/v1/pedidos/9102", null, token("admin@raizes.invalid")).statusCode());
        assertError(send("GET", "/api/v1/pedidos/9101", null, token("totem@raizes.invalid")),
                403, "ACESSO_NEGADO");
        assertError(send("PUT", "/api/v1/usuarios/me/consentimentos/fidelidade", "{\"consentido\":true}",
                token("totem@raizes.invalid")), 403, "ACESSO_NEGADO");
        assertError(send("GET", "/api/v1/pedidos/9101", null, null), 401, "NAO_AUTENTICADO");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE pedido_id = 9101 AND acao = 'ACESSO_DADO_PESSOAL'",
                Integer.class) >= 1);
    }

    private String token(String email) throws Exception {
        var response = send("POST", "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"senha\":\"Demo@1234\"}", null);
        assertEquals(200, response.statusCode(), response.body());
        return json(response).path("accessToken").asText();
    }

    private JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }

    private void assertError(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        JsonNode body = json(response);
        assertEquals(code, body.path("error").asText());
        assertTrue(body.has("message"));
        assertTrue(body.has("details"));
        assertTrue(body.has("timestamp"));
        assertTrue(body.has("path"));
        assertEquals(response.headers().firstValue("X-Request-Id").orElse(""), body.path("requestId").asText());
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json");
            request.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
