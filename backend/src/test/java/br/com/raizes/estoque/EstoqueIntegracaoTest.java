package br.com.raizes.estoque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.raizes.estoque.application.ReservaEstoqueService;
import br.com.raizes.shared.domain.ConflitoException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EstoqueIntegracaoTest {
    @Value("${local.server.port}")
    int port;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    ReservaEstoqueService reserva;
    @Autowired
    EntityManagerFactory entityManagerFactory;

    private final HttpClient http = HttpClient.newHttpClient();
    private final String motivoPrefix = "teste-estoque-" + UUID.randomUUID();
    private final List<UUID> requestIds = new ArrayList<>();
    private Saldo inicial110;
    private Saldo inicial210;
    private Saldo inicial111;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM movimento_estoque WHERE pedido_id = 9401");
        jdbc.update("DELETE FROM pedido WHERE id = 9401");
        inicial110 = ler(1, 10);
        inicial210 = ler(2, 10);
        inicial111 = ler(1, 11);
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM movimento_estoque WHERE pedido_id = 9401 OR motivo LIKE ?", motivoPrefix + "%");
        jdbc.update("DELETE FROM pedido WHERE id = 9401");
        for (UUID requestId : requestIds) {
            jdbc.update("DELETE FROM auditoria WHERE request_id = ?", requestId);
        }
        restaurar(1, 10, inicial110);
        restaurar(2, 10, inicial210);
        restaurar(1, 11, inicial111);
    }

    @Test
    void produtosECatalogosTrazemPrecosLocaisEPaginacao() throws Exception {
        var produtos = send("GET", "/api/v1/produtos?page=1&limit=2", null, null);
        assertEquals(200, produtos.statusCode());
        assertEquals(3, json(produtos).path("totalItems").asInt());
        assertEquals(2, json(produtos).path("data").size());
        assertFalse(json(produtos).path("data").get(0).has("preco"));
        var centro = send("GET", "/api/v1/unidades/1/cardapio?page=1&limit=20", null, null);
        var orla = send("GET", "/api/v1/unidades/2/cardapio?page=1&limit=20", null, null);
        assertEquals(200, centro.statusCode());
        assertEquals(200, orla.statusCode());
        assertEquals(3, json(centro).path("totalItems").asInt());
        assertEquals(2, json(orla).path("totalItems").asInt());
        assertEquals(0, precoDoProduto(json(centro), 10).compareTo(new BigDecimal("39.90")));
        assertEquals(0, precoDoProduto(json(orla), 10).compareTo(new BigDecimal("42.00")));
        var estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean estatisticasAntes = estatisticas.isStatisticsEnabled();
        estatisticas.setStatisticsEnabled(true);
        try {
            estatisticas.clear();
            var paginaCompleta = send("GET", "/api/v1/unidades/1/cardapio?page=1&limit=20", null, null);
            assertEquals(200, paginaCompleta.statusCode());
            assertEquals(3, json(paginaCompleta).path("data").size());
            assertTrue(estatisticas.getPrepareStatementCount() >= 2);
            assertTrue(estatisticas.getPrepareStatementCount() <= 4,
                    "Cardápio com três produtos deve usar lote de saldos, não uma consulta por produto.");
        } finally {
            estatisticas.setStatisticsEnabled(estatisticasAntes);
        }
        assertError(send("GET", "/api/v1/produtos?page=0", null, null), 422, "PAGINACAO_INVALIDA");
    }

    @Test
    void movimentoManualAfetaSomenteUnidadeEProtegeReserva() throws Exception {
        String gerenteCentro = token("gerente@raizes.invalid");
        String gerenteOrla = token("gerente.orla@raizes.invalid");
        String atendente = token("atendente@raizes.invalid");
        String admin = token("admin@raizes.invalid");
        assertEquals(200, send("GET", "/api/v1/estoque?unidadeId=1&produtoId=10", null, gerenteCentro).statusCode());
        var pagina = send("GET", "/api/v1/estoque?unidadeId=1&page=1&limit=1", null, gerenteCentro);
        assertEquals(200, pagina.statusCode());
        assertEquals(1, json(pagina).path("data").size());
        assertEquals(3, json(pagina).path("totalItems").asInt());
        assertError(send("GET", "/api/v1/estoque?unidadeId=1&page=0", null, gerenteCentro),
                422, "PAGINACAO_INVALIDA");
        assertError(send("GET", "/api/v1/estoque", null, gerenteCentro), 422, "IDENTIFICADOR_INVALIDO");
        assertError(send("GET", "/api/v1/estoque?unidadeId=2", null, gerenteCentro), 403, "ACESSO_NEGADO");
        assertError(send("GET", "/api/v1/estoque?unidadeId=1", null, atendente), 403, "ACESSO_NEGADO");
        assertError(send("GET", "/api/v1/estoque?unidadeId=1", null, null), 401, "NAO_AUTENTICADO");
        assertEquals(200, send("GET", "/api/v1/estoque?unidadeId=2", null, admin).statusCode());
        assertEquals(200, send("GET", "/api/v1/estoque?unidadeId=2", null, gerenteOrla).statusCode());

        inserirPedidoFixture();
        reserva.reservar(1L, Map.of(10L, 2), 9401L, 100L);
        assertEquals(inicial110.reservado() + 2, ler(1, 10).reservado());

        var entrada = movimento(1, 10, "ENTRADA", 3, gerenteCentro, "entrada");
        assertEquals(201, entrada.statusCode(), entrada.body());
        assertEquals(inicial110.fisico() + 3, json(entrada).path("saldo").path("fisico").asInt());
        assertEquals(inicial210, ler(2, 10));
        assertError(movimento(2, 10, "ENTRADA", 1, gerenteCentro, "outra-unidade"), 403, "ACESSO_NEGADO");
        assertEquals(inicial210, ler(2, 10));

        int disponivel = inicial110.fisico() + 3 - inicial110.reservado() - 2;
        var saida = movimento(1, 10, "SAIDA_AJUSTE", disponivel, gerenteCentro, "saida");
        assertEquals(201, saida.statusCode(), saida.body());
        assertEquals(0, json(saida).path("saldo").path("disponivel").asInt());
        assertEquals(inicial110.reservado() + 2, json(saida).path("saldo").path("fisico").asInt());
        assertEquals(inicial110.reservado() + 2, json(saida).path("saldo").path("reservado").asInt());
        assertEquals(inicial210, ler(2, 10));
        assertFalse(disponivelNoCardapio(1, 10));
        assertTrue(disponivelNoCardapio(2, 10));

        assertError(movimento(1, 10, "SAIDA_AJUSTE", 1, gerenteCentro, "excesso"),
                409, "ESTOQUE_INSUFICIENTE");
        assertError(movimento(1, 10, "RESERVA", 1, gerenteCentro, "tipo-invalido"),
                422, "TIPO_MOVIMENTO_INVALIDO");
        var historico = send("GET", "/api/v1/estoque/movimentacoes?unidadeId=1&produtoId=10&page=1&limit=1",
                null, gerenteCentro);
        assertEquals(200, historico.statusCode());
        assertEquals(1, json(historico).path("data").size());
        assertTrue(json(historico).path("totalItems").asInt() >= 2);
        assertError(send("GET", "/api/v1/estoque/movimentacoes?unidadeId=2", null, gerenteCentro),
                403, "ACESSO_NEGADO");
        assertEquals(200, send("GET", "/api/v1/estoque/movimentacoes?unidadeId=2", null, admin).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE request_id = ?", Integer.class,
                UUID.fromString(entrada.headers().firstValue("X-Request-Id").orElseThrow())));
    }

    @Test
    void reservaLiberacaoEBaixaSaoTransacionais() {
        inserirPedidoFixture();
        reserva.reservar(1L, Map.of(10L, 2), 9401L, 100L);
        assertEquals(inicial110.fisico(), ler(1, 10).fisico());
        assertEquals(inicial110.reservado() + 2, ler(1, 10).reservado());
        assertEquals(inicial210, ler(2, 10));
        assertThrows(ConflitoException.class,
                () -> reserva.reservar(2L, Map.of(10L, 1), 9401L, 100L));
        assertEquals(inicial210, ler(2, 10));
        reserva.liberar(1L, Map.of(10L, 2), 9401L, 100L);
        assertEquals(inicial110, ler(1, 10));
        assertThrows(ConflitoException.class,
                () -> reserva.liberar(1L, Map.of(10L, 1), 9401L, 100L));

        int movimentosAntes = contarMovimentosDoPedido();
        assertThrows(ConflitoException.class,
                () -> reserva.reservar(1L, Map.of(10L, 1, 11L, 999999), 9401L, 100L));
        assertEquals(inicial110, ler(1, 10));
        assertEquals(inicial111, ler(1, 11));
        assertEquals(movimentosAntes, contarMovimentosDoPedido());

        reserva.reservar(1L, Map.of(10L, 2), 9401L, 100L);
        reserva.baixar(1L, Map.of(10L, 2), 9401L, 100L);
        assertEquals(inicial110.fisico() - 2, ler(1, 10).fisico());
        assertEquals(inicial110.reservado(), ler(1, 10).reservado());
        assertEquals(inicial210, ler(2, 10));
    }

    private void inserirPedidoFixture() {
        jdbc.update("""
                INSERT INTO pedido (id, unidade_id, cliente_id, criado_por_id, canal_pedido,
                modalidade_atendimento, status, forma_pagamento, total)
                VALUES (9401, 1, 100, 100, 'APP', 'RETIRADA', 'AGUARDANDO_PAGAMENTO', 'MOCK', 39.90)
                """);
    }

    private int contarMovimentosDoPedido() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM movimento_estoque WHERE pedido_id = 9401", Integer.class);
    }

    private boolean disponivelNoCardapio(long unidade, long produto) throws Exception {
        var response = send("GET", "/api/v1/unidades/" + unidade + "/cardapio?page=1&limit=20", null, null);
        assertEquals(200, response.statusCode());
        for (JsonNode item : json(response).path("data")) {
            if (item.path("produtoId").asLong() == produto) return item.path("disponivel").asBoolean();
        }
        throw new AssertionError("Produto não apareceu no cardápio.");
    }

    private BigDecimal precoDoProduto(JsonNode pagina, long produto) {
        for (JsonNode item : pagina.path("data")) {
            if (item.path("produtoId").asLong() == produto) return item.path("preco").decimalValue();
        }
        throw new AssertionError("Produto não apareceu no cardápio.");
    }

    private HttpResponse<String> movimento(long unidade, long produto, String tipo, int quantidade,
                                            String token, String sufixo) throws Exception {
        String body = """
                {"unidadeId":%d,"produtoId":%d,"tipo":"%s","quantidade":%d,"motivo":"%s"}
                """.formatted(unidade, produto, tipo, quantidade, motivoPrefix + "-" + sufixo);
        var response = send("POST", "/api/v1/estoque/movimentacoes", body, token);
        if (response.statusCode() == 201) {
            requestIds.add(UUID.fromString(response.headers().firstValue("X-Request-Id").orElseThrow()));
        }
        return response;
    }

    private String token(String email) throws Exception {
        var response = send("POST", "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"senha\":\"Demo@1234\"}", null);
        assertEquals(200, response.statusCode(), response.body());
        return json(response).path("accessToken").asText();
    }

    private Saldo ler(long unidade, long produto) {
        return jdbc.queryForObject("SELECT fisico, reservado FROM estoque_saldo WHERE unidade_id = ? AND produto_id = ?",
                (rs, row) -> new Saldo(rs.getInt(1), rs.getInt(2)), unidade, produto);
    }

    private void restaurar(long unidade, long produto, Saldo salvo) {
        jdbc.update("UPDATE estoque_saldo SET fisico = ?, reservado = ?, versao = versao + 1 "
                        + "WHERE unidade_id = ? AND produto_id = ?",
                salvo.fisico(), salvo.reservado(), unidade, produto);
    }

    private JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }

    private void assertError(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        var body = json(response);
        assertEquals(code, body.path("error").asText());
        assertEquals(response.headers().firstValue("X-Request-Id").orElse(""), body.path("requestId").asText());
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private record Saldo(int fisico, int reservado) { }
}
