package br.com.raizes.pagamentos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.raizes.pedidos.application.ExpirarReservasService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
class FluxoPedidoIntegracaoTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ExpirarReservasService expiracao;
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<Long> pedidos = new ArrayList<>();
    private Saldo inicialCentro;
    private Saldo inicialOrla;

    @BeforeEach
    void preparar() {
        inicialCentro = saldo(1);
        inicialOrla = saldo(2);
    }

    @AfterEach
    void limpar() {
        for (Long id : pedidos) {
            jdbc.update("DELETE FROM auditoria WHERE pedido_id = ?", id);
            jdbc.update("DELETE FROM movimento_estoque WHERE pedido_id = ?", id);
            jdbc.update("DELETE FROM pagamento_evento WHERE pagamento_id IN (SELECT id FROM pagamento WHERE pedido_id = ?)", id);
            jdbc.update("DELETE FROM pagamento WHERE pedido_id = ?", id);
            jdbc.update("DELETE FROM item_pedido WHERE pedido_id = ?", id);
            jdbc.update("DELETE FROM pedido WHERE id = ?", id);
        }
        restaurar(1, inicialCentro);
        restaurar(2, inicialOrla);
    }

    @Test
    void aprovacaoBaixaUmaVezEAvancaAteEntrega() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        String cozinha = token("cozinha@raizes.invalid");
        String atendente = token("atendente@raizes.invalid");
        String gerenteOrla = token("gerente.orla@raizes.invalid");
        long id = criar(cliente, "APP", 2);
        assertEquals(inicialCentro.reservado() + 2, saldo(1).reservado());
        UUID chave = UUID.randomUUID();
        var pagamento = pagar(id, chave, "APROVAR", cliente);
        assertEquals(200, pagamento.statusCode(), pagamento.body());
        assertEquals("APROVADO", json(pagamento).path("statusPagamento").asText());
        assertEquals("EM_PREPARO", json(pagamento).path("statusPedido").asText());
        assertEquals(inicialCentro.fisico() - 2, saldo(1).fisico());
        assertEquals(inicialCentro.reservado(), saldo(1).reservado());
        assertEquals(inicialOrla, saldo(2));
        assertEquals(1, movimentos(id, "BAIXA"));
        var payload = jdbc.queryForMap("SELECT status, payload_envio, payload_retorno FROM pagamento WHERE pedido_id = ?", id);
        assertEquals("APROVADO", payload.get("status"));
        assertTrue(payload.get("payload_envio").toString().contains("\"pedidoId\":" + id));
        assertTrue(payload.get("payload_retorno").toString().contains("MOCK-OK"));
        assertEquals(1, eventos(id));
        assertEquals(200, pagar(id, chave, "APROVAR", cliente).statusCode());
        assertEquals(1, movimentos(id, "BAIXA"));
        erro(pagar(id, UUID.randomUUID(), "APROVAR", cliente), 409, "PAGAMENTO_JA_SOLICITADO");
        erro(send("PATCH", "/api/v1/pedidos/" + id + "/status", "{\"status\":\"ENTREGUE\"}", cozinha, null),
                403, "ACESSO_NEGADO");
        erro(send("PATCH", "/api/v1/pedidos/" + id + "/status", "{\"status\":\"PRONTO\"}", gerenteOrla, null),
                403, "ACESSO_NEGADO");
        var pronto = send("PATCH", "/api/v1/pedidos/" + id + "/status", "{\"status\":\"PRONTO\"}", cozinha, null);
        assertEquals(200, pronto.statusCode(), pronto.body());
        assertEquals("PRONTO", json(pronto).path("status").asText());
        erro(send("PATCH", "/api/v1/pedidos/" + id + "/status", "{\"status\":\"PRONTO\"}", cozinha, null),
                409, "TRANSICAO_INVALIDA");
        var entregue = send("PATCH", "/api/v1/pedidos/" + id + "/status", "{\"status\":\"ENTREGUE\"}", atendente, null);
        assertEquals(200, entregue.statusCode(), entregue.body());
        assertEquals("ENTREGUE", json(entregue).path("status").asText());
        assertEquals("ENTREGUE", jdbc.queryForObject("SELECT status FROM pedido WHERE id = ?", String.class, id));
        erro(send("POST", "/api/v1/pedidos/" + id + "/cancelamento", null, cliente, null),
                409, "TRANSICAO_INVALIDA");
    }

    @Test
    void recusaLiberaReservaSemBaixaOuSegundaTentativa() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        long id = criar(cliente, "APP", 1);
        UUID chave = UUID.randomUUID();
        var pagamento = pagar(id, chave, "RECUSAR", cliente);
        assertEquals(200, pagamento.statusCode(), pagamento.body());
        assertEquals("RECUSADO", json(pagamento).path("statusPagamento").asText());
        assertEquals("CANCELADO", json(pagamento).path("statusPedido").asText());
        assertEquals("Pagamento simulado recusado.", jdbc.queryForObject(
                "SELECT motivo_cancelamento FROM pedido WHERE id = ?", String.class, id));
        var detalhe = send("GET", "/api/v1/pedidos/" + id, null, cliente, null);
        assertEquals("Pagamento simulado recusado.", json(detalhe).path("motivoCancelamento").asText());
        assertEquals(inicialCentro, saldo(1));
        assertEquals(1, movimentos(id, "LIBERACAO"));
        assertEquals(0, movimentos(id, "BAIXA"));
        assertEquals(200, pagar(id, chave, "RECUSAR", cliente).statusCode());
        assertEquals(1, movimentos(id, "LIBERACAO"));
        erro(pagar(id, chave, "APROVAR", cliente), 409, "PAGAMENTO_JA_SOLICITADO");
        erro(send("PATCH", "/api/v1/pedidos/" + id + "/status", "{\"status\":\"PRONTO\"}",
                token("cozinha@raizes.invalid"), null), 409, "TRANSICAO_INVALIDA");
    }

    @Test
    void indeterminadoMantemReservaAteReconciliacaoDeRecusaOuAprovacao() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        String gerente = token("gerente@raizes.invalid");
        long recusadoId = criar(cliente, "APP", 1);
        var indeterminado = pagar(recusadoId, UUID.randomUUID(), "INDETERMINADO_RECUSAR", cliente);
        assertEquals(202, indeterminado.statusCode(), indeterminado.body());
        assertEquals("INDETERMINADO", json(indeterminado).path("statusPagamento").asText());
        assertEquals("PAGAMENTO_INDETERMINADO", json(indeterminado).path("statusPedido").asText());
        assertEquals(inicialCentro.fisico(), saldo(1).fisico());
        assertEquals(inicialCentro.reservado() + 1, saldo(1).reservado());
        assertEquals(0, movimentos(recusadoId, "LIBERACAO"));
        assertEquals(0, expiracao.expirarAte(Instant.now().plus(Duration.ofHours(2))));
        assertEquals(inicialCentro.reservado() + 1, saldo(1).reservado());
        erro(send("POST", "/api/v1/pedidos/" + recusadoId + "/cancelamento", null, cliente, null),
                409, "TRANSICAO_INVALIDA");
        erro(reconciliar(recusadoId, cliente), 403, "ACESSO_NEGADO");
        erro(reconciliar(recusadoId, token("gerente.orla@raizes.invalid")), 403, "ACESSO_NEGADO");
        var resolvido = reconciliar(recusadoId, gerente);
        assertEquals(200, resolvido.statusCode(), resolvido.body());
        assertEquals("RECUSADO", json(resolvido).path("statusPagamento").asText());
        assertEquals("CANCELADO", json(resolvido).path("statusPedido").asText());
        assertEquals(inicialCentro, saldo(1));
        assertEquals(1, movimentos(recusadoId, "LIBERACAO"));
        assertEquals(2, eventos(recusadoId));
        assertEquals(200, reconciliar(recusadoId, gerente).statusCode());
        assertEquals(1, movimentos(recusadoId, "LIBERACAO"));

        long aprovadoId = criar(cliente, "APP", 1);
        assertEquals(202, pagar(aprovadoId, UUID.randomUUID(), "INDETERMINADO_APROVAR", cliente).statusCode());
        var aprovado = reconciliar(aprovadoId, gerente);
        assertEquals(200, aprovado.statusCode(), aprovado.body());
        assertEquals("APROVADO", json(aprovado).path("statusPagamento").asText());
        assertEquals(inicialCentro.fisico() - 1, saldo(1).fisico());
        assertEquals(inicialCentro.reservado(), saldo(1).reservado());
        assertEquals(1, movimentos(aprovadoId, "BAIXA"));
    }

    @Test
    void falhaTecnicaPermanecePendenteDeResolucaoESemLiberacao() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        long id = criar(cliente, "APP", 1);
        var falha = pagar(id, UUID.randomUUID(), "FALHA_TECNICA", cliente);
        assertEquals(202, falha.statusCode(), falha.body());
        assertEquals("PAGAMENTO_INDETERMINADO", json(falha).path("statusPedido").asText());
        assertEquals(202, reconciliar(id, token("gerente@raizes.invalid")).statusCode());
        assertEquals(0, movimentos(id, "LIBERACAO"));
        assertEquals(0, movimentos(id, "BAIXA"));
        assertEquals(inicialCentro.reservado() + 1, saldo(1).reservado());
    }

    @Test
    void pagamentosConcorrentesComMesmaChaveBaixamUmaVez() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        long id = criar(cliente, "APP", 1);
        UUID chave = UUID.randomUUID();
        var a = CompletableFuture.supplyAsync(() -> pagarSemExcecao(id, chave, cliente));
        var b = CompletableFuture.supplyAsync(() -> pagarSemExcecao(id, chave, cliente));
        var primeira = a.get();
        var segunda = b.get();
        assertTrue(primeira.statusCode() == 200 || primeira.statusCode() == 202, primeira.body());
        assertTrue(segunda.statusCode() == 200 || segunda.statusCode() == 202, segunda.body());
        assertEquals("EM_PREPARO", jdbc.queryForObject("SELECT status FROM pedido WHERE id = ?", String.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pagamento WHERE pedido_id = ?", Integer.class, id));
        assertEquals(1, movimentos(id, "BAIXA"));
        assertEquals(1, eventos(id));
        assertEquals(inicialCentro.fisico() - 1, saldo(1).fisico());
        assertEquals(inicialCentro.reservado(), saldo(1).reservado());
    }

    private HttpResponse<String> pagarSemExcecao(long id, UUID chave, String cliente) {
        try {
            return pagar(id, chave, "APROVAR", cliente);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void reconciliacoesConcorrentesConfirmamUmaBaixa() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        String gerente = token("gerente@raizes.invalid");
        long id = criar(cliente, "APP", 1);
        assertEquals(202, pagar(id, UUID.randomUUID(), "INDETERMINADO_APROVAR", cliente).statusCode());
        var a = CompletableFuture.supplyAsync(() -> reconciliarSemExcecao(id, gerente));
        var b = CompletableFuture.supplyAsync(() -> reconciliarSemExcecao(id, gerente));
        assertEquals(200, a.get().statusCode());
        assertEquals(200, b.get().statusCode());
        assertEquals("EM_PREPARO", jdbc.queryForObject("SELECT status FROM pedido WHERE id = ?", String.class, id));
        assertEquals(1, movimentos(id, "BAIXA"));
        assertEquals(2, eventos(id));
        assertEquals(inicialCentro.fisico() - 1, saldo(1).fisico());
        assertEquals(inicialCentro.reservado(), saldo(1).reservado());
    }

    private HttpResponse<String> reconciliarSemExcecao(long id, String gerente) {
        try {
            return reconciliar(id, gerente);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void expiracaoIgnoraTentativaPendenteEProcessaOutroPedido() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        long pendente = criar(cliente, "APP", 1);
        jdbc.update("INSERT INTO pagamento (pedido_id, chave_idempotencia, status, valor, cenario_mock, payload_envio) "
                        + "VALUES (?, ?, 'PENDENTE', 39.90, 'APROVAR', '{}')", pendente, UUID.randomUUID());
        long semTentativa = criar(cliente, "APP", 1);
        assertEquals(1, expiracao.expirarAte(Instant.now().plus(Duration.ofMinutes(31))));
        assertEquals("AGUARDANDO_PAGAMENTO",
                jdbc.queryForObject("SELECT status FROM pedido WHERE id = ?", String.class, pendente));
        assertEquals("CANCELADO", jdbc.queryForObject("SELECT status FROM pedido WHERE id = ?", String.class, semTentativa));
        assertEquals(0, movimentos(pendente, "LIBERACAO"));
        assertEquals(1, movimentos(semTentativa, "LIBERACAO"));
        assertEquals(inicialCentro.reservado() + 1, saldo(1).reservado());
    }

    @Test
    void cancelamentoEExpiracaoLiberamSomentePedidoSemTentativa() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        long cancelado = criar(cliente, "APP", 1);
        assertEquals(900.0, jdbc.queryForObject(
                "SELECT EXTRACT(EPOCH FROM (reserva_expira_em - criado_em)) FROM pedido WHERE id = ?",
                Double.class, cancelado));
        var resposta = send("POST", "/api/v1/pedidos/" + cancelado + "/cancelamento",
                "{\"motivo\":\"Desistencia\"}", cliente, null);
        assertEquals(200, resposta.statusCode(), resposta.body());
        assertTrue(json(resposta).path("reservaLiberada").asBoolean());
        assertEquals("Desistencia", jdbc.queryForObject(
                "SELECT motivo_cancelamento FROM pedido WHERE id = ?", String.class, cancelado));
        assertEquals(inicialCentro, saldo(1));
        assertEquals(1, movimentos(cancelado, "LIBERACAO"));
        erro(send("POST", "/api/v1/pedidos/" + cancelado + "/cancelamento", null, cliente, null),
                409, "TRANSICAO_INVALIDA");
        erro(pagar(cancelado, UUID.randomUUID(), "APROVAR", cliente), 409, "TRANSICAO_INVALIDA");
        long expirado = criar(cliente, "APP", 1);
        assertEquals(0, expiracao.expirarAte(Instant.now().plus(Duration.ofMinutes(14))));
        assertEquals(1, expiracao.expirarAte(Instant.now().plus(Duration.ofMinutes(16))));
        assertEquals("CANCELADO", jdbc.queryForObject("SELECT status FROM pedido WHERE id = ?", String.class, expirado));
        assertEquals("Reserva expirou sem tentativa de pagamento.", jdbc.queryForObject(
                "SELECT motivo_cancelamento FROM pedido WHERE id = ?", String.class, expirado));
        assertEquals(inicialCentro, saldo(1));
        assertEquals(1, movimentos(expirado, "LIBERACAO"));
        assertEquals(0, expiracao.expirarAte(Instant.now().plus(Duration.ofHours(2))));
        assertEquals(1, movimentos(expirado, "LIBERACAO"));
    }

    private long criar(String token, String canal, int quantidade) throws Exception {
        var response = send("POST", "/api/v1/pedidos",
                "{\"unidadeId\":1,\"canalPedido\":\"" + canal
                        + "\",\"modalidadeAtendimento\":\"RETIRADA\",\"formaPagamento\":\"MOCK\","
                        + "\"itens\":[{\"produtoId\":10,\"quantidade\":" + quantidade + "}]}",
                token, UUID.randomUUID());
        assertEquals(201, response.statusCode(), response.body());
        long id = json(response).path("pedidoId").asLong();
        pedidos.add(id);
        return id;
    }

    private HttpResponse<String> pagar(long id, UUID chave, String cenario, String token) throws Exception {
        return send("POST", "/api/v1/pedidos/" + id + "/pagamentos",
                "{\"cenarioMock\":\"" + cenario + "\"}", token, chave);
    }

    private HttpResponse<String> reconciliar(long id, String token) throws Exception {
        return send("POST", "/api/v1/pedidos/" + id + "/pagamentos/reconciliacao", null, token, null);
    }

    private String token(String email) throws Exception {
        var response = send("POST", "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"senha\":\"Demo@1234\"}", null, null);
        assertEquals(200, response.statusCode(), response.body());
        return json(response).path("accessToken").asText();
    }

    private Saldo saldo(long unidade) {
        return jdbc.queryForObject("SELECT fisico, reservado FROM estoque_saldo WHERE unidade_id = ? AND produto_id = 10",
                (rs, row) -> new Saldo(rs.getInt(1), rs.getInt(2)), unidade);
    }

    private void restaurar(long unidade, Saldo saldo) {
        jdbc.update("UPDATE estoque_saldo SET fisico = ?, reservado = ?, versao = versao + 1 "
                        + "WHERE unidade_id = ? AND produto_id = 10", saldo.fisico(), saldo.reservado(), unidade);
    }

    private int movimentos(long id, String tipo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM movimento_estoque WHERE pedido_id = ? AND tipo = ?",
                Integer.class, id, tipo);
    }

    private int eventos(long pedidoId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM pagamento_evento e JOIN pagamento p ON p.id = e.pagamento_id "
                + "WHERE p.pedido_id = ?", Integer.class, pedidoId);
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

    private record Saldo(int fisico, int reservado) { }
}
