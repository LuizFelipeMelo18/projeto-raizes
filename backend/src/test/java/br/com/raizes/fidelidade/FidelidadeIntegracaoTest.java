package br.com.raizes.fidelidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.raizes.fidelidade.application.FidelidadeService;
import br.com.raizes.pedidos.application.ExpirarReservasService;
import br.com.raizes.pedidos.infrastructure.jpa.PedidoJpaRepository;
import java.math.BigDecimal;
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
class FidelidadeIntegracaoTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired FidelidadeService fidelidadeService;
    @Autowired PedidoJpaRepository pedidoRepository;
    @Autowired ExpirarReservasService expiracao;
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<Long> pedidos = new ArrayList<>();
    private final List<Long> usuarios = List.of(100L, 108L);
    private int fisicoInicial;
    private int reservadoInicial;

    @BeforeEach
    void preparar() {
        fisicoInicial = jdbc.queryForObject(
                "SELECT fisico FROM estoque_saldo WHERE unidade_id = 1 AND produto_id = 10", Integer.class);
        reservadoInicial = jdbc.queryForObject(
                "SELECT reservado FROM estoque_saldo WHERE unidade_id = 1 AND produto_id = 10", Integer.class);
        limparDadosFidelidade();
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
        limparDadosFidelidade();
        jdbc.update("UPDATE estoque_saldo SET fisico = ?, reservado = ?, versao = versao + 1 "
                + "WHERE unidade_id = 1 AND produto_id = 10", fisicoInicial, reservadoInicial);
    }

    private void limparDadosFidelidade() {
        for (Long u : usuarios) {
            jdbc.update("DELETE FROM fidelidade_movimento WHERE usuario_id = ?", u);
            jdbc.update("DELETE FROM fidelidade_conta WHERE usuario_id = ?", u);
            jdbc.update("DELETE FROM consentimento WHERE usuario_id = ?", u);
            jdbc.update("DELETE FROM auditoria WHERE sujeito_usuario_id = ? AND recurso IN ('FIDELIDADE', 'FIDELIDADE_HISTORICO', 'CONSENTIMENTO')", u);
        }
    }

    @Test
    void pedidoAnonimoOuSemConsentimentoNaoParticipaNemAcumulaOuResgata() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        String totem = token("totem@raizes.invalid");
        String cozinha = token("cozinha@raizes.invalid");
        String atendente = token("atendente@raizes.invalid");

        var estadoInicial = send("GET", "/api/v1/fidelidade/consentimento", null, cliente, null);
        assertEquals(200, estadoInicial.statusCode(), estadoInicial.body());
        assertFalse(json(estadoInicial).path("consentido").asBoolean());

        erro(criarComResgate(cliente, "APP", 1, 100), 409, "CONSENTIMENTO_FIDELIDADE_INATIVO");
        erro(criarComResgate(totem, "TOTEM", 1, 100), 409, "FIDELIDADE_EXIGE_CLIENTE");

        long semConsentimento = criar(cliente, "APP", 2, 0);
        assertFalse(detalhe(semConsentimento, cliente).path("participaFidelidade").asBoolean());
        entregar(semConsentimento, cliente, cozinha, atendente);

        long anonimo = criar(totem, "TOTEM", 2, 0);
        assertFalse(detalhe(anonimo, atendente).path("participaFidelidade").asBoolean());
        entregar(anonimo, totem, cozinha, atendente);

        // Consentir depois não gera pontos retroativos para pedido criado sem consentimento.
        consentir(cliente, true);
        var saldo = consultarSaldo(cliente);
        assertTrue(saldo.path("consentimentoAtivo").asBoolean());
        assertEquals(0, saldo.path("saldo").asInt());
        assertEquals(0, saldo.path("totalAcumulado").asInt());
        assertTrue(saldo.path("saldoConsistente").asBoolean());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM fidelidade_movimento", Integer.class));
    }

    @Test
    void revogacaoAntesDaEntregaImpedeAcumulo() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        String cozinha = token("cozinha@raizes.invalid");
        String atendente = token("atendente@raizes.invalid");

        consentir(cliente, true);
        long id = criar(cliente, "APP", 3, 0);
        assertTrue(detalhe(id, cliente).path("participaFidelidade").asBoolean());
        assertEquals(200, pagar(id, UUID.randomUUID(), "APROVAR", cliente).statusCode());
        assertEquals(200, status(id, "PRONTO", cozinha).statusCode());

        // Cliente revoga antes da entrega: não deve haver crédito.
        var revogado = send("PUT", "/api/v1/fidelidade/consentimento", "{\"consentido\":false}", cliente, null);
        assertEquals(200, revogado.statusCode(), revogado.body());
        assertFalse(json(revogado).path("consentido").asBoolean());

        assertEquals(200, status(id, "ENTREGUE", atendente).statusCode());
        var saldo = consultarSaldo(cliente);
        assertFalse(saldo.path("consentimentoAtivo").asBoolean());
        assertEquals(0, saldo.path("saldo").asInt());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM fidelidade_movimento WHERE pedido_id = ?", Integer.class, id));
    }

    @Test
    void acumulaSoAposEntregueImpedeCreditoDuplicadoEResgataComDescontoELimite() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        String cozinha = token("cozinha@raizes.invalid");
        String atendente = token("atendente@raizes.invalid");

        consentir(cliente, true);
        // 3 * 39.90 = 119.70 -> 119 pontos somente quando ENTREGUE.
        long pedidoAcumulo = criar(cliente, "APP", 3, 0);
        assertEquals(200, pagar(pedidoAcumulo, UUID.randomUUID(), "APROVAR", cliente).statusCode());
        assertEquals(0, consultarSaldo(cliente).path("saldo").asInt());
        assertEquals(200, status(pedidoAcumulo, "PRONTO", cozinha).statusCode());
        assertEquals(0, consultarSaldo(cliente).path("saldo").asInt());

        var entrega1 = CompletableFuture.supplyAsync(() -> statusSemExcecao(pedidoAcumulo, "ENTREGUE", atendente));
        var entrega2 = CompletableFuture.supplyAsync(() -> statusSemExcecao(pedidoAcumulo, "ENTREGUE", atendente));
        int code1 = entrega1.get().statusCode();
        int code2 = entrega2.get().statusCode();
        assertTrue((code1 == 200 && code2 == 409) || (code1 == 409 && code2 == 200),
                "Apenas uma transição ENTREGUE pode suceder: " + code1 + "/" + code2);

        // Reprocessamento direto do serviço sobre pedido já ENTREGUE também não duplica o crédito.
        var entidade = pedidoRepository.findById(pedidoAcumulo).orElseThrow();
        assertEquals(0, fidelidadeService.acumularNaEntregaSeAplicavel(entidade, 102L, UUID.randomUUID()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM fidelidade_movimento WHERE pedido_id = ? AND tipo = 'ACUMULO'",
                Integer.class, pedidoAcumulo));
        assertEquals(119, consultarSaldo(cliente).path("saldo").asInt());

        // Validações de formato, limite de 50% e saldo.
        erro(criarComResgate(cliente, "APP", 1, 50), 422, "PONTOS_RESGATE_INVALIDOS");
        erro(criarComResgate(cliente, "APP", 1, -100), 422, "PONTOS_RESGATE_INVALIDOS");
        // 1 item = 39.90; 50% = 19.95 -> máximo 300 pontos (R$ 15,00). 400 pontos (R$ 20,00) excede 50%.
        erro(criarComResgate(cliente, "APP", 1, 400), 409, "LIMITE_RESGATE_EXCEDIDO");
        // 200 pontos (R$ 10,00) está dentro do limite de 50%, mas saldo é 119.
        erro(criarComResgate(cliente, "APP", 1, 200), 409, "SALDO_PONTOS_INSUFICIENTE");

        // Resgate válido de 100 pontos = R$ 5,00 em pedido de R$ 39,90 -> total a pagar R$ 34,90.
        long pedidoResgate = criar(cliente, "APP", 1, 100);
        JsonNode det = detalhe(pedidoResgate, cliente);
        assertEquals(0, new BigDecimal("39.90").compareTo(det.path("subtotalItens").decimalValue()));
        assertEquals(0, new BigDecimal("5.00").compareTo(det.path("descontoFidelidade").decimalValue()));
        assertEquals(100, det.path("pontosResgatados").asInt());
        assertEquals(0, new BigDecimal("34.90").compareTo(det.path("total").decimalValue()));
        assertEquals(19, consultarSaldo(cliente).path("saldo").asInt());

        var pgto = pagar(pedidoResgate, UUID.randomUUID(), "APROVAR", cliente);
        assertEquals(200, pgto.statusCode(), pgto.body());
        assertEquals(0, new BigDecimal("34.90").compareTo(json(pgto).path("valor").decimalValue()));
        assertEquals(200, status(pedidoResgate, "PRONTO", cozinha).statusCode());
        assertEquals(200, status(pedidoResgate, "ENTREGUE", atendente).statusCode());

        // Entrega do segundo pedido credita floor(34.90) = 34 pontos -> saldo final 19 + 34 = 53.
        JsonNode saldoFinal = consultarSaldo(cliente);
        assertEquals(53, saldoFinal.path("saldo").asInt());
        assertEquals(153, saldoFinal.path("totalAcumulado").asInt());
        assertEquals(100, saldoFinal.path("totalResgatado").asInt());
        assertEquals(0, saldoFinal.path("totalEstornado").asInt());
        assertEquals(53, saldoFinal.path("saldoCalculadoMovimentos").asInt());
        assertTrue(saldoFinal.path("saldoConsistente").asBoolean());
        conferirSaldoExplicadoPorMovimentos(100L, 53);
    }

    @Test
    void devolvePontosEmRecusaReconciliacaoCancelamentoEExpiracaoSemDuplicar() throws Exception {
        String cliente = token("cliente@raizes.invalid");
        String cozinha = token("cozinha@raizes.invalid");
        String atendente = token("atendente@raizes.invalid");
        String gerente = token("gerente@raizes.invalid");

        consentir(cliente, true);
        // Acumula 319 pontos (8 * 39.90 = 319.20).
        long base = criar(cliente, "APP", 8, 0);
        entregar(base, cliente, cozinha, atendente);
        assertEquals(319, consultarSaldo(cliente).path("saldo").asInt());

        // 1) Recusa direta do gateway devolve 100 pontos.
        long recusado = criar(cliente, "APP", 1, 100);
        assertEquals(219, consultarSaldo(cliente).path("saldo").asInt());
        UUID chaveRecusa = UUID.randomUUID();
        assertEquals(200, pagar(recusado, chaveRecusa, "RECUSAR", cliente).statusCode());
        assertEquals(319, consultarSaldo(cliente).path("saldo").asInt());
        // Replay da mesma tentativa recusada não estorna de novo.
        assertEquals(200, pagar(recusado, chaveRecusa, "RECUSAR", cliente).statusCode());
        assertEquals(319, consultarSaldo(cliente).path("saldo").asInt());
        assertEquals(1, estornos(recusado));

        // 2) Indeterminado retém o débito; reconciliação recusada devolve uma vez.
        long indetRecusado = criar(cliente, "APP", 1, 100);
        assertEquals(219, consultarSaldo(cliente).path("saldo").asInt());
        assertEquals(202, pagar(indetRecusado, UUID.randomUUID(), "INDETERMINADO_RECUSAR", cliente).statusCode());
        assertEquals(219, consultarSaldo(cliente).path("saldo").asInt());
        assertEquals(200, send("POST", "/api/v1/pedidos/" + indetRecusado + "/pagamentos/reconciliacao",
                null, gerente, null).statusCode());
        assertEquals(319, consultarSaldo(cliente).path("saldo").asInt());
        assertEquals(200, send("POST", "/api/v1/pedidos/" + indetRecusado + "/pagamentos/reconciliacao",
                null, gerente, null).statusCode());
        assertEquals(319, consultarSaldo(cliente).path("saldo").asInt());
        assertEquals(1, estornos(indetRecusado));

        // 3) Cancelamento antes do pagamento devolve 100 pontos.
        long cancelado = criar(cliente, "APP", 1, 100);
        assertEquals(219, consultarSaldo(cliente).path("saldo").asInt());
        assertEquals(200, send("POST", "/api/v1/pedidos/" + cancelado + "/cancelamento",
                "{\"motivo\":\"Desistencia\"}", cliente, null).statusCode());
        assertEquals(319, consultarSaldo(cliente).path("saldo").asInt());
        assertEquals(1, estornos(cancelado));

        // 4) Expiração automática de reserva sem tentativa também devolve 100 pontos.
        long expirado = criar(cliente, "APP", 1, 100);
        assertEquals(219, consultarSaldo(cliente).path("saldo").asInt());
        assertEquals(1, expiracao.expirarAte(Instant.now().plus(Duration.ofMinutes(31))));
        assertEquals(319, consultarSaldo(cliente).path("saldo").asInt());
        assertEquals(1, estornos(expirado));

        var historico = send("GET", "/api/v1/fidelidade/movimentacoes?page=1&limit=20", null, cliente, null);
        assertEquals(200, historico.statusCode(), historico.body());
        assertEquals(9, json(historico).path("totalItems").asInt());
        conferirSaldoExplicadoPorMovimentos(100L, 319);
    }

    @Test
    void autorizacaoIsolaClientesEBloqueiaPerfisNaoClientes() throws Exception {
        String cliente1 = token("cliente@raizes.invalid");
        String cliente2 = token("cliente2@raizes.invalid");
        String cozinha = token("cozinha@raizes.invalid");
        String atendente = token("atendente@raizes.invalid");

        consentir(cliente1, true);
        long p1 = criar(cliente1, "APP", 2, 0);
        entregar(p1, cliente1, cozinha, atendente);

        assertEquals(79, consultarSaldo(cliente1).path("saldo").asInt());
        assertEquals(0, consultarSaldo(cliente2).path("saldo").asInt());
        var mov2 = send("GET", "/api/v1/fidelidade/movimentacoes", null, cliente2, null);
        assertEquals(200, mov2.statusCode());
        assertEquals(0, json(mov2).path("totalItems").asInt());

        erro(send("GET", "/api/v1/fidelidade/saldo", null, null, null), 401, "NAO_AUTENTICADO");
        for (String email : List.of("totem@raizes.invalid", "atendente@raizes.invalid",
                "cozinha@raizes.invalid", "gerente@raizes.invalid", "admin@raizes.invalid")) {
            String t = token(email);
            erro(send("GET", "/api/v1/fidelidade/saldo", null, t, null), 403, "ACESSO_NEGADO");
            erro(send("GET", "/api/v1/fidelidade/movimentacoes", null, t, null), 403, "ACESSO_NEGADO");
            erro(send("GET", "/api/v1/fidelidade/consentimento", null, t, null), 403, "ACESSO_NEGADO");
            erro(send("PUT", "/api/v1/fidelidade/consentimento", "{\"consentido\":true}", t, null),
                    403, "ACESSO_NEGADO");
        }
    }

    private void conferirSaldoExplicadoPorMovimentos(long usuarioId, int esperado) {
        int saldoConta = jdbc.queryForObject(
                "SELECT saldo FROM fidelidade_conta WHERE usuario_id = ?", Integer.class, usuarioId);
        int somaMovimentos = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE tipo
                    WHEN 'ACUMULO' THEN pontos
                    WHEN 'RESGATE' THEN -pontos
                    WHEN 'ESTORNO_RESGATE' THEN pontos
                END), 0)
                FROM fidelidade_movimento WHERE usuario_id = ?
                """, Integer.class, usuarioId);
        assertEquals(esperado, saldoConta);
        assertEquals(saldoConta, somaMovimentos);
    }

    private int estornos(long pedidoId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM fidelidade_movimento WHERE pedido_id = ? AND tipo = 'ESTORNO_RESGATE'",
                Integer.class, pedidoId);
    }

    private void consentir(String token, boolean consentido) throws Exception {
        var r = send("PUT", "/api/v1/fidelidade/consentimento",
                "{\"consentido\":" + consentido + "}", token, null);
        assertEquals(200, r.statusCode(), r.body());
    }

    private JsonNode consultarSaldo(String token) throws Exception {
        var r = send("GET", "/api/v1/fidelidade/saldo", null, token, null);
        assertEquals(200, r.statusCode(), r.body());
        return json(r);
    }

    private JsonNode detalhe(long id, String token) throws Exception {
        var r = send("GET", "/api/v1/pedidos/" + id, null, token, null);
        assertEquals(200, r.statusCode(), r.body());
        return json(r);
    }

    private void entregar(long id, String pagador, String cozinha, String atendente) throws Exception {
        assertEquals(200, pagar(id, UUID.randomUUID(), "APROVAR", pagador).statusCode());
        assertEquals(200, status(id, "PRONTO", cozinha).statusCode());
        assertEquals(200, status(id, "ENTREGUE", atendente).statusCode());
    }

    private long criar(String token, String canal, int quantidade, int pontos) throws Exception {
        var r = criarComResgate(token, canal, quantidade, pontos);
        assertEquals(201, r.statusCode(), r.body());
        long id = json(r).path("pedidoId").asLong();
        pedidos.add(id);
        return id;
    }

    private HttpResponse<String> criarComResgate(String token, String canal, int quantidade, int pontos) throws Exception {
        return send("POST", "/api/v1/pedidos",
                "{\"unidadeId\":1,\"canalPedido\":\"" + canal
                        + "\",\"modalidadeAtendimento\":\"RETIRADA\",\"formaPagamento\":\"MOCK\","
                        + "\"pontosResgate\":" + pontos + ","
                        + "\"itens\":[{\"produtoId\":10,\"quantidade\":" + quantidade + "}]}",
                token, UUID.randomUUID());
    }

    private HttpResponse<String> pagar(long id, UUID chave, String cenario, String token) throws Exception {
        return send("POST", "/api/v1/pedidos/" + id + "/pagamentos",
                "{\"cenarioMock\":\"" + cenario + "\"}", token, chave);
    }

    private HttpResponse<String> status(long id, String destino, String token) throws Exception {
        return send("PATCH", "/api/v1/pedidos/" + id + "/status", "{\"status\":\"" + destino + "\"}", token, null);
    }

    private HttpResponse<String> statusSemExcecao(long id, String destino, String token) {
        try {
            return status(id, destino, token);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
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

