package br.com.raizes.pedidos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
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
class PedidoIntegracaoTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<Long> criados = new ArrayList<>();
    private final int saldoInicial = 30;

    @AfterEach
    void limpar() {
        for (Long id : criados) {
            jdbc.update("DELETE FROM movimento_estoque WHERE pedido_id = ?", id);
            jdbc.update("DELETE FROM auditoria WHERE pedido_id = ?", id);
            jdbc.update("DELETE FROM item_pedido WHERE pedido_id = ?", id);
            jdbc.update("DELETE FROM pedido WHERE id = ?", id);
        }
        jdbc.update("UPDATE estoque_saldo SET fisico = ?, reservado = 0, versao = versao + 1 "
                + "WHERE unidade_id = 1 AND produto_id = 10", saldoInicial);
    }

    @Test
    void criaPersisteReservaUmaVezFiltraEConservaPreco() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        String outro = token("cliente2@raizes.invalid");
        String gerenteOutraUnidade = token("gerente.orla@raizes.invalid");
        String body = pedido(1, "APP", 2);
        UUID chave = UUID.randomUUID();
        var criado = send("POST", "/api/v1/pedidos", body, cliente, chave);
        assertEquals(201, criado.statusCode(), criado.body());
        long id = json(criado).path("pedidoId").asLong();
        criados.add(id);
        assertEquals(0, new java.math.BigDecimal("39.90").compareTo(
                json(criado).path("itens").get(0).path("precoUnitario").decimalValue()));
        assertEquals(0, new java.math.BigDecimal("79.80").compareTo(json(criado).path("total").decimalValue()));
        assertEquals(100L, jdbc.queryForObject("SELECT cliente_id FROM pedido WHERE id = ?", Long.class, id));
        assertEquals(2, reservado());
        assertEquals(1, movimentos(id));
        assertEquals(0, jdbc.queryForObject("SELECT reservado FROM estoque_saldo WHERE unidade_id = 2 AND produto_id = 10", Integer.class));
        var precoAnterior = jdbc.queryForObject(
                "SELECT preco FROM cardapio_item WHERE unidade_id = 1 AND produto_id = 10",
                java.math.BigDecimal.class);
        try {
            jdbc.update("UPDATE cardapio_item SET preco = 41.00 WHERE unidade_id = 1 AND produto_id = 10");
            var detalhe = send("GET", "/api/v1/pedidos/" + id, null, cliente, null);
            assertEquals(200, detalhe.statusCode());
            assertEquals(0, new java.math.BigDecimal("39.90").compareTo(
                    json(detalhe).path("itens").get(0).path("precoUnitario").decimalValue()));
        } finally {
            jdbc.update("UPDATE cardapio_item SET preco = ? WHERE unidade_id = 1 AND produto_id = 10", precoAnterior);
        }
        var repetido = send("POST", "/api/v1/pedidos", body, cliente, chave);
        assertEquals(200, repetido.statusCode(), repetido.body());
        assertEquals(id, json(repetido).path("pedidoId").asLong());
        assertEquals("true", repetido.headers().firstValue("Idempotency-Replayed").orElse(""));
        assertEquals(2, reservado());
        assertEquals(1, movimentos(id));
        erro(send("POST", "/api/v1/pedidos", pedido(1, "APP", 3), cliente, chave), 409, "IDEMPOTENCIA_CONFLITO");
        assertEquals(2, reservado());
        var lista = send("GET", "/api/v1/pedidos?canalPedido=APP&status=AGUARDANDO_PAGAMENTO&page=1&limit=20", null, cliente, null);
        assertEquals(200, lista.statusCode(), lista.body());
        assertTrue(contem(json(lista).path("data"), id));
        var vazia = send("GET", "/api/v1/pedidos?canalPedido=WEB", null, cliente, null);
        assertFalse(contem(json(vazia).path("data"), id));
        assertFalse(contem(json(send("GET", "/api/v1/pedidos", null, outro, null)).path("data"), id));
        erro(send("GET", "/api/v1/pedidos/" + id, null, outro, null), 404, "PEDIDO_NAO_ENCONTRADO");
        erro(send("GET", "/api/v1/pedidos/" + id, null, gerenteOutraUnidade, null), 403, "ACESSO_NEGADO");
        erro(send("GET", "/api/v1/pedidos?unidadeId=1", null, gerenteOutraUnidade, null), 403, "ACESSO_NEGADO");
    }

    @Test
    void totemAnonimoTemCodigoSemDadosPessoaisEUnidadeIsolada() throws Exception {
        String totem = token("totem@raizes.invalid");
        String atendente = token("atendente@raizes.invalid");
        erro(send("POST", "/api/v1/pedidos", pedido(2, "TOTEM", 1), totem, UUID.randomUUID()), 403, "ACESSO_NEGADO");
        erro(send("POST", "/api/v1/pedidos", pedido(1, "APP", 1), totem, UUID.randomUUID()), 403, "ACESSO_NEGADO");
        var criado = send("POST", "/api/v1/pedidos", pedido(1, "TOTEM", 1), totem, UUID.randomUUID());
        assertEquals(201, criado.statusCode(), criado.body());
        long id = json(criado).path("pedidoId").asLong();
        criados.add(id);
        String codigo = json(criado).path("codigoAcompanhamento").asText();
        assertEquals(24, codigo.length());
        assertEquals(101L, jdbc.queryForObject("SELECT criado_por_id FROM pedido WHERE id = ?", Long.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pedido WHERE id = ? AND cliente_id IS NOT NULL", Integer.class, id));
        var acompanhamento = send("GET", "/api/v1/pedidos/acompanhar/" + codigo, null, null, null);
        assertEquals(200, acompanhamento.statusCode());
        assertEquals("AGUARDANDO_PAGAMENTO", json(acompanhamento).path("status").asText());
        assertFalse(json(acompanhamento).has("pedidoId"));
        assertFalse(acompanhamento.body().contains("cliente"));
        erro(send("GET", "/api/v1/pedidos/acompanhar/invalid", null, null, null), 404, "PEDIDO_NAO_ENCONTRADO");
        assertEquals(200, send("GET", "/api/v1/pedidos/" + id, null, atendente, null).statusCode());
        erro(send("GET", "/api/v1/pedidos/" + id, null, totem, null), 403, "ACESSO_NEGADO");
        assertEquals(1, reservado());
    }

    @Test
    void duasCriacoesConcorrentesDaMesmaChaveReservamUmaVez() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        UUID chave = UUID.randomUUID();
        String body = pedido(1, "APP", 1);
        var a = CompletableFuture.supplyAsync(() -> enviarSemExcecao(body, cliente, chave));
        var b = CompletableFuture.supplyAsync(() -> enviarSemExcecao(body, cliente, chave));
        var respostaA = a.get();
        var respostaB = b.get();
        assertTrue(respostaA.statusCode() == 201 || respostaA.statusCode() == 200, respostaA.body());
        assertTrue(respostaB.statusCode() == 201 || respostaB.statusCode() == 200, respostaB.body());
        long id = json(respostaA).path("pedidoId").asLong();
        criados.add(id);
        assertEquals(id, json(respostaB).path("pedidoId").asLong());
        assertEquals(1, reservado());
        assertEquals(1, movimentos(id));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pedido WHERE chave_idempotencia = ?", Integer.class, chave));
    }

    @Test
    void doisPedidosConcorrentesDisputandoOUltimoItemPermitemApenasUmaReserva() throws Exception {
        String cliente1 = token("cliente@raizes.invalid");
        String cliente2 = token("cliente2@raizes.invalid");
        jdbc.update("UPDATE estoque_saldo SET fisico = 1, reservado = 0, versao = versao + 1 "
                + "WHERE unidade_id = 1 AND produto_id = 10");
        String body = pedido(1, "APP", 1);
        var f1 = CompletableFuture.supplyAsync(() -> enviarSemExcecao(body, cliente1, UUID.randomUUID()));
        var f2 = CompletableFuture.supplyAsync(() -> enviarSemExcecao(body, cliente2, UUID.randomUUID()));
        var r1 = f1.get();
        var r2 = f2.get();
        if (r1.statusCode() == 201) criados.add(json(r1).path("pedidoId").asLong());
        if (r2.statusCode() == 201) criados.add(json(r2).path("pedidoId").asLong());
        assertTrue((r1.statusCode() == 201 && r2.statusCode() == 409)
                        || (r1.statusCode() == 409 && r2.statusCode() == 201),
                "Exatamente um pedido deve conseguir reservar o último item: " + r1.statusCode() + "/" + r2.statusCode());
        var rejeitado = r1.statusCode() == 409 ? r1 : r2;
        assertEquals("ESTOQUE_INSUFICIENTE", json(rejeitado).path("error").asText());
        assertEquals(1, reservado());
        assertEquals(1, criados.size());
    }

    private HttpResponse<String> enviarSemExcecao(String body, String cliente, UUID chave) {
        try {
            return send("POST", "/api/v1/pedidos", body, cliente, chave);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void rejeitaErrosSemPersistirOuReservar() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        erro(send("POST", "/api/v1/pedidos", pedido(1, "APP", 1), null, UUID.randomUUID()), 401, "NAO_AUTENTICADO");
        erro(send("POST", "/api/v1/pedidos", pedido(1, "INVALIDO", 1), cliente, UUID.randomUUID()), 422, "CANAL_PEDIDO_INVALIDO");
        UUID semSaldo = UUID.randomUUID();
        erro(send("POST", "/api/v1/pedidos", pedido(1, "APP", 31), cliente, semSaldo), 409, "ESTOQUE_INSUFICIENTE");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pedido WHERE chave_idempotencia = ?", Integer.class, semSaldo));
        erro(send("POST", "/api/v1/pedidos", pedido(1, "APP", 1), cliente, null), 422, "IDEMPOTENCY_KEY_INVALIDA");
        erro(send("GET", "/api/v1/pedidos?status=INEXISTENTE", null, cliente, null), 422, "STATUS_PEDIDO_INVALIDO");
        assertEquals(0, reservado());
    }

    private String pedido(long unidade, String canal, int quantidade) {
        return "{\"unidadeId\":" + unidade + ",\"canalPedido\":\"" + canal
                + "\",\"modalidadeAtendimento\":\"RETIRADA\",\"formaPagamento\":\"MOCK\","
                + "\"itens\":[{\"produtoId\":10,\"quantidade\":" + quantidade + "}]}";
    }

    private int reservado() {
        return jdbc.queryForObject("SELECT reservado FROM estoque_saldo WHERE unidade_id = 1 AND produto_id = 10", Integer.class);
    }

    private int movimentos(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM movimento_estoque WHERE pedido_id = ?", Integer.class, id);
    }

    private boolean contem(JsonNode data, long id) {
        for (JsonNode item : data) if (item.path("pedidoId").asLong() == id) return true;
        return false;
    }

    private String token(String email) throws Exception {
        var response = send("POST", "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"senha\":\"Demo@1234\"}", null, null);
        assertEquals(200, response.statusCode(), response.body());
        return json(response).path("accessToken").asText();
    }

    private void erro(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(code, json(response).path("error").asText());
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
