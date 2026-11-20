package br.com.raizes.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuditoriaERelatorioIntegracaoTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<Long> pedidos = new ArrayList<>();
    private final List<Long> movimentosManuais = new ArrayList<>();
    private int fisicoCentro;
    private int reservadoCentro;
    private int fisicoOrla;
    private int reservadoOrla;

    @BeforeEach
    void preparar() {
        fisicoCentro = jdbc.queryForObject(
                "SELECT fisico FROM estoque_saldo WHERE unidade_id = 1 AND produto_id = 10", Integer.class);
        reservadoCentro = jdbc.queryForObject(
                "SELECT reservado FROM estoque_saldo WHERE unidade_id = 1 AND produto_id = 10", Integer.class);
        fisicoOrla = jdbc.queryForObject(
                "SELECT fisico FROM estoque_saldo WHERE unidade_id = 2 AND produto_id = 10", Integer.class);
        reservadoOrla = jdbc.queryForObject(
                "SELECT reservado FROM estoque_saldo WHERE unidade_id = 2 AND produto_id = 10", Integer.class);
    }

    @AfterEach
    void limpar() {
        for (Long id : pedidos) {
            jdbc.update("DELETE FROM fidelidade_movimento WHERE pedido_id = ?", id);
            jdbc.update("DELETE FROM auditoria WHERE pedido_id = ?", id);
            jdbc.update("DELETE FROM movimento_estoque WHERE pedido_id = ?", id);
            jdbc.update("DELETE FROM pagamento_evento WHERE pagamento_id IN (SELECT id FROM pagamento WHERE pedido_id = ?)", id);
            jdbc.update("DELETE FROM pagamento WHERE pedido_id = ?", id);
            jdbc.update("DELETE FROM item_pedido WHERE pedido_id = ?", id);
            jdbc.update("DELETE FROM pedido WHERE id = ?", id);
        }
        for (Long movId : movimentosManuais) {
            jdbc.update("DELETE FROM movimento_estoque WHERE id = ?", movId);
        }
        jdbc.update("DELETE FROM fidelidade_movimento WHERE usuario_id = 100");
        jdbc.update("DELETE FROM fidelidade_conta WHERE usuario_id = 100");
        jdbc.update("DELETE FROM consentimento WHERE usuario_id = 100");
        jdbc.update("DELETE FROM auditoria WHERE recurso IN ('ESTOQUE', 'FIDELIDADE', 'FIDELIDADE_HISTORICO', 'CONSENTIMENTO') "
                + "OR acao IN ('LOGIN', 'ACESSO_DADO_PESSOAL')");
        jdbc.update("UPDATE estoque_saldo SET fisico = ?, reservado = ?, versao = versao + 1 "
                + "WHERE unidade_id = 1 AND produto_id = 10", fisicoCentro, reservadoCentro);
        jdbc.update("UPDATE estoque_saldo SET fisico = ?, reservado = ?, versao = versao + 1 "
                + "WHERE unidade_id = 2 AND produto_id = 10", fisicoOrla, reservadoOrla);
    }

    @Test
    void registraEventosDePedidoStatusCancelamentoEstoqueEDadosPessoaisSemDadosSensiveis() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        String cozinha = token("cozinha@raizes.invalid");
        String atendente = token("atendente@raizes.invalid");
        String gerente = token("gerente@raizes.invalid");
        String admin = token("admin@raizes.invalid");

        // 1) Acesso a dados pessoais (perfil próprio) + consentimento
        assertEquals(200, send("GET", "/api/v1/usuarios/me", null, cliente, null).statusCode());
        assertEquals(200, send("PUT", "/api/v1/fidelidade/consentimento",
                "{\"consentido\":true}", cliente, null).statusCode());

        // 2) Ajuste manual de estoque na unidade 1
        var ajuste = send("POST", "/api/v1/estoque/movimentacoes",
                "{\"unidadeId\":1,\"produtoId\":10,\"tipo\":\"ENTRADA\",\"quantidade\":5,\"motivo\":\"Reposicao turno\"}",
                gerente, null);
        assertEquals(201, ajuste.statusCode(), ajuste.body());
        movimentosManuais.add(json(ajuste).path("movimentoId").asLong());

        // 3) Pedido entregue (CRIACAO_PEDIDO -> RESULTADO_PAGAMENTO -> TRANSICAO_PEDIDO PRONTO/ENTREGUE -> ACUMULO_FIDELIDADE)
        long entregue = criar(cliente, 1, "APP", 2);
        assertEquals(200, send("GET", "/api/v1/pedidos/" + entregue, null, cliente, null).statusCode());
        assertEquals(200, pagar(entregue, "APROVAR", cliente).statusCode());
        assertEquals(200, status(entregue, "PRONTO", cozinha).statusCode());
        assertEquals(200, status(entregue, "ENTREGUE", atendente).statusCode());

        // 4) Pedido cancelado (CRIACAO_PEDIDO -> CANCELAMENTO_PEDIDO)
        long cancelado = criar(cliente, 1, "WEB", 1);
        assertEquals(200, send("POST", "/api/v1/pedidos/" + cancelado + "/cancelamento",
                "{\"motivo\":\"Mudanca de ideia\"}", cliente, null).statusCode());

        // Consulta da auditoria da unidade 1 pelo gerente da unidade 1
        var paginaUnidade = send("GET", "/api/v1/auditoria?page=1&limit=50", null, gerente, null);
        assertEquals(200, paginaUnidade.statusCode(), paginaUnidade.body());
        String corpo = paginaUnidade.body();
        assertFalse(corpo.contains("Demo@1234"));
        assertFalse(corpo.contains("$2a$"));
        assertFalse(corpo.contains(cliente));

        JsonNode eventos = json(paginaUnidade).path("data");
        assertTrue(eventos.size() >= 8);
        for (JsonNode ev : eventos) {
            assertEquals(1L, ev.path("unidadeId").asLong());
            assertTrue(ev.hasNonNull("recurso"));
            assertTrue(ev.hasNonNull("acao"));
            assertTrue(ev.hasNonNull("criadoEm"));
        }

        // Conferência por filtro de ação/recurso contra a tabela auditoria
        conferirFiltroAuditoria(gerente, "CRIACAO_PEDIDO", "PEDIDO", 1L);
        conferirFiltroAuditoria(gerente, "CANCELAMENTO_PEDIDO", "PEDIDO", 1L);
        conferirFiltroAuditoria(gerente, "TRANSICAO_PEDIDO", "PEDIDO", 1L);
        conferirFiltroAuditoria(gerente, "MOVIMENTO_ESTOQUE", "ESTOQUE", 1L);
        conferirFiltroAuditoria(gerente, "ACESSO_DADO_PESSOAL", "PEDIDO", 1L);
        conferirFiltroAuditoria(admin, "ACESSO_DADO_PESSOAL", "USUARIO", null);
    }

    @Test
    void relatorioDePedidosPorUnidadeECanalCorrespondeAosDadosPersistidos() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        String totem = token("totem@raizes.invalid");
        String atendenteOrla = token("atendente.orla@raizes.invalid");
        String cozinha = token("cozinha@raizes.invalid");
        String atendente = token("atendente@raizes.invalid");
        String gerenteCentro = token("gerente@raizes.invalid");
        String gerenteOrla = token("gerente.orla@raizes.invalid");
        String admin = token("admin@raizes.invalid");

        assertEquals(200, send("PUT", "/api/v1/fidelidade/consentimento",
                "{\"consentido\":true}", cliente, null).statusCode());

        // Unidade 1, APP: pedido 1 (3 * 39.90 = 119.70) entregue -> gera 119 pontos
        long p1 = criar(cliente, 1, "APP", 3);
        assertEquals(200, pagar(p1, "APROVAR", cliente).statusCode());
        assertEquals(200, status(p1, "PRONTO", cozinha).statusCode());
        assertEquals(200, status(p1, "ENTREGUE", atendente).statusCode());

        // Unidade 1, APP: pedido 2 com resgate de 100 pontos (subtotal 39.90, desconto 5.00, total 34.90)
        var r2 = send("POST", "/api/v1/pedidos",
                "{\"unidadeId\":1,\"canalPedido\":\"APP\",\"modalidadeAtendimento\":\"RETIRADA\","
                        + "\"formaPagamento\":\"MOCK\",\"pontosResgate\":100,"
                        + "\"itens\":[{\"produtoId\":10,\"quantidade\":1}]}",
                cliente, UUID.randomUUID());
        assertEquals(201, r2.statusCode(), r2.body());
        pedidos.add(json(r2).path("pedidoId").asLong());

        // Unidade 1, TOTEM: 1 * 39.90 = 39.90
        criar(totem, 1, "TOTEM", 1);

        // Unidade 2, BALCAO: 2 * 42.00 = 84.00
        criar(atendenteOrla, 2, "BALCAO", 2);

        // Relatório do gerente da unidade 1 enxerga somente a unidade 1
        var relCentro = send("GET", "/api/v1/pedidos/relatorios/unidades-canais", null, gerenteCentro, null);
        assertEquals(200, relCentro.statusCode(), relCentro.body());
        JsonNode bodyCentro = json(relCentro);
        assertEquals(1L, bodyCentro.path("unidadeFiltro").asLong());
        assertEquals(3L, bodyCentro.path("totalPedidos").asLong());
        assertEquals(0, new BigDecimal("199.50").compareTo(bodyCentro.path("subtotalItensTotal").decimalValue()));
        assertEquals(0, new BigDecimal("5.00").compareTo(bodyCentro.path("descontoFidelidadeTotal").decimalValue()));
        assertEquals(0, new BigDecimal("194.50").compareTo(bodyCentro.path("valorTotal").decimalValue()));
        assertEquals(100L, bodyCentro.path("pontosResgatadosTotal").asLong());

        // Relatório do gerente da unidade 2 enxerga somente a unidade 2
        var relOrla = send("GET", "/api/v1/pedidos/relatorios/unidades-canais", null, gerenteOrla, null);
        assertEquals(200, relOrla.statusCode(), relOrla.body());
        JsonNode bodyOrla = json(relOrla);
        assertEquals(2L, bodyOrla.path("unidadeFiltro").asLong());
        assertEquals(1L, bodyOrla.path("totalPedidos").asLong());
        assertEquals(0, new BigDecimal("84.00").compareTo(bodyOrla.path("valorTotal").decimalValue()));

        // Relatório global do ADMIN enxerga as duas unidades e bate com o SQL direto
        var relAdmin = send("GET", "/api/v1/pedidos/relatorios/unidades-canais", null, admin, null);
        assertEquals(200, relAdmin.statusCode(), relAdmin.body());
        JsonNode bodyAdmin = json(relAdmin);
        long countSql = jdbc.queryForObject("SELECT COUNT(*) FROM pedido", Long.class);
        BigDecimal totalSql = jdbc.queryForObject("SELECT COALESCE(SUM(total), 0) FROM pedido", BigDecimal.class);
        BigDecimal descontoSql = jdbc.queryForObject(
                "SELECT COALESCE(SUM(desconto_fidelidade), 0) FROM pedido", BigDecimal.class);
        assertEquals(countSql, bodyAdmin.path("totalPedidos").asLong());
        assertEquals(0, totalSql.compareTo(bodyAdmin.path("valorTotal").decimalValue()));
        assertEquals(0, descontoSql.compareTo(bodyAdmin.path("descontoFidelidadeTotal").decimalValue()));

        // Filtro por canalPedido=APP e status=ENTREGUE
        var relFiltrado = send("GET",
                "/api/v1/pedidos/relatorios/unidades-canais?unidadeId=1&canalPedido=APP&status=ENTREGUE",
                null, admin, null);
        assertEquals(200, relFiltrado.statusCode(), relFiltrado.body());
        assertEquals(1L, json(relFiltrado).path("totalPedidos").asLong());
        assertEquals(0, new BigDecimal("119.70").compareTo(json(relFiltrado).path("valorTotal").decimalValue()));
    }

    @Test
    void autorizacaoRestringeAuditoriaERelatorioAGerenteDaUnidadeOuAdmin() throws Exception {
        String gerenteCentro = token("gerente@raizes.invalid");
        String gerenteOrla = token("gerente.orla@raizes.invalid");

        erro(send("GET", "/api/v1/auditoria", null, null, null), 401, "NAO_AUTENTICADO");
        erro(send("GET", "/api/v1/pedidos/relatorios/unidades-canais", null, null, null), 401, "NAO_AUTENTICADO");

        // Gerente não pode consultar outra unidade
        erro(send("GET", "/api/v1/auditoria?unidadeId=2", null, gerenteCentro, null), 403, "ACESSO_NEGADO");
        erro(send("GET", "/api/v1/auditoria?unidadeId=1", null, gerenteOrla, null), 403, "ACESSO_NEGADO");
        erro(send("GET", "/api/v1/pedidos/relatorios/unidades-canais?unidadeId=2", null, gerenteCentro, null),
                403, "ACESSO_NEGADO");
        erro(send("GET", "/api/v1/pedidos/relatorios/unidades-canais?unidadeId=1", null, gerenteOrla, null),
                403, "ACESSO_NEGADO");

        // CLIENTE, TOTEM, ATENDENTE e COZINHA recebem 403
        for (String email : List.of("cliente@raizes.invalid", "totem@raizes.invalid",
                "atendente@raizes.invalid", "cozinha@raizes.invalid")) {
            String t = token(email);
            erro(send("GET", "/api/v1/auditoria", null, t, null), 403, "ACESSO_NEGADO");
            erro(send("GET", "/api/v1/pedidos/relatorios/unidades-canais", null, t, null), 403, "ACESSO_NEGADO");
        }
    }

    private void conferirFiltroAuditoria(String token, String acao, String recurso, Long unidadeId) throws Exception {
        String query = "/api/v1/auditoria?acao=" + acao + "&recurso=" + recurso
                + (unidadeId != null ? "&unidadeId=" + unidadeId : "");
        var resp = send("GET", query, null, token, null);
        assertEquals(200, resp.statusCode(), resp.body());
        int totalApi = json(resp).path("totalItems").asInt();
        int totalSql = unidadeId != null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE acao = ? AND recurso = ? AND unidade_id = ?",
                        Integer.class, acao, recurso, unidadeId)
                : jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE acao = ? AND recurso = ?",
                        Integer.class, acao, recurso);
        assertTrue(totalApi >= 1, "Esperava ao menos 1 evento para " + acao + "/" + recurso);
        assertEquals(totalSql, totalApi);
    }

    private long criar(String token, long unidade, String canal, int quantidade) throws Exception {
        var r = send("POST", "/api/v1/pedidos",
                "{\"unidadeId\":" + unidade + ",\"canalPedido\":\"" + canal
                        + "\",\"modalidadeAtendimento\":\"RETIRADA\",\"formaPagamento\":\"MOCK\","
                        + "\"itens\":[{\"produtoId\":10,\"quantidade\":" + quantidade + "}]}",
                token, UUID.randomUUID());
        assertEquals(201, r.statusCode(), r.body());
        long id = json(r).path("pedidoId").asLong();
        pedidos.add(id);
        return id;
    }

    private HttpResponse<String> pagar(long id, String cenario, String token) throws Exception {
        return send("POST", "/api/v1/pedidos/" + id + "/pagamentos",
                "{\"cenarioMock\":\"" + cenario + "\"}", token, UUID.randomUUID());
    }

    private HttpResponse<String> status(long id, String destino, String token) throws Exception {
        return send("PATCH", "/api/v1/pedidos/" + id + "/status", "{\"status\":\"" + destino + "\"}", token, null);
    }

    private String token(String email) throws Exception {
        var response = send("POST", "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"senha\":\"Demo@1234\"}", null, null);
        assertEquals(200, response.statusCode(), response.body());
        return json(response).path("accessToken").asText();
    }

    private void erro(HttpResponse<String> response, int status, String codigo) {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(codigo, json(response).path("error").asText());
    }

    private JsonNode json(HttpResponse<String> response) { return mapper.readTree(response.body()); }

    private HttpResponse<String> send(String metodo, String path, String body, String token, UUID chave) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (chave != null) builder.header("Idempotency-Key", chave.toString());
        if (body == null) builder.method(metodo, HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json")
                .method(metodo, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}

