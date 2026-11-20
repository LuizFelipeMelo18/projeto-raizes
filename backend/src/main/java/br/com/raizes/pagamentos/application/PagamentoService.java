package br.com.raizes.pagamentos.application;

import br.com.raizes.auditoria.application.RegistrarAuditoriaService;
import br.com.raizes.estoque.application.ReservaEstoqueService;
import br.com.raizes.fidelidade.application.FidelidadeService;
import br.com.raizes.identidade.domain.Perfil;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.pagamentos.domain.CenarioMock;
import br.com.raizes.pagamentos.domain.StatusPagamento;
import br.com.raizes.pagamentos.infrastructure.jpa.PagamentoEntity;
import br.com.raizes.pagamentos.infrastructure.jpa.PagamentoEventoEntity;
import br.com.raizes.pagamentos.infrastructure.jpa.PagamentoEventoJpaRepository;
import br.com.raizes.pagamentos.infrastructure.jpa.PagamentoJpaRepository;
import br.com.raizes.pedidos.domain.PedidoAccessPolicy;
import br.com.raizes.pedidos.domain.StatusPedido;
import br.com.raizes.pedidos.infrastructure.IdempotenciaPedidoLock;
import br.com.raizes.pedidos.infrastructure.jpa.ItemPedidoJpaRepository;
import br.com.raizes.pedidos.infrastructure.jpa.PedidoEntity;
import br.com.raizes.pedidos.infrastructure.jpa.PedidoJpaRepository;
import br.com.raizes.shared.domain.AcessoNegadoException;
import br.com.raizes.shared.domain.ConflitoException;
import br.com.raizes.shared.domain.RecursoNaoEncontradoException;
import br.com.raizes.shared.domain.ValidacaoException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PagamentoService {
    private final TransactionTemplate transacao;
    private final PaymentGateway gateway;
    private final PedidoJpaRepository pedidos;
    private final PagamentoJpaRepository pagamentos;
    private final PagamentoEventoJpaRepository eventos;
    private final ItemPedidoJpaRepository itens;
    private final ReservaEstoqueService estoque;
    private final FidelidadeService fidelidade;
    private final IdempotenciaPedidoLock idempotenciaLock;
    private final RegistrarAuditoriaService auditoria;

    public PagamentoService(PlatformTransactionManager gerenciador, PaymentGateway gateway,
                            PedidoJpaRepository pedidos, PagamentoJpaRepository pagamentos,
                            PagamentoEventoJpaRepository eventos,
                            ItemPedidoJpaRepository itens, ReservaEstoqueService estoque,
                            FidelidadeService fidelidade, IdempotenciaPedidoLock idempotenciaLock,
                            RegistrarAuditoriaService auditoria) {
        this.transacao = new TransactionTemplate(gerenciador);
        this.gateway = gateway;
        this.pedidos = pedidos;
        this.pagamentos = pagamentos;
        this.eventos = eventos;
        this.itens = itens;
        this.estoque = estoque;
        this.fidelidade = fidelidade;
        this.idempotenciaLock = idempotenciaLock;
        this.auditoria = auditoria;
    }

    public Resposta solicitar(Long pedidoId, UUID chave, String cenarioTexto,
                              UsuarioAutenticado ator, UUID requestId) {
        if (chave == null) {
            throw new ValidacaoException("IDEMPOTENCY_KEY_INVALIDA", "Idempotency-Key UUID é obrigatória.");
        }
        CenarioMock cenario = cenario(cenarioTexto);
        Inicio inicio = transacao.execute(status -> iniciar(pedidoId, chave, cenario, ator));
        if (!inicio.chamarGateway()) return inicio.respostaExistente();
        exigirSemTransacao();
        var resultado = chamarGateway(() -> gateway.solicitar(inicio.solicitacao()));
        return transacao.execute(status -> concluir(inicio.solicitacao().pedidoId(), resultado,
                "SOLICITACAO", ator.id(), requestId));
    }

    public Resposta reconciliar(Long pedidoId, UsuarioAutenticado ator, UUID requestId) {
        Inicio inicio = transacao.execute(status -> prepararReconciliacao(pedidoId, ator));
        if (!inicio.chamarGateway()) return inicio.respostaExistente();
        exigirSemTransacao();
        var resultado = chamarGateway(() -> gateway.consultar(inicio.solicitacao()));
        return transacao.execute(status -> concluir(pedidoId, resultado, "RECONCILIACAO", ator.id(), requestId));
    }

    private Inicio iniciar(Long pedidoId, UUID chave, CenarioMock cenario, UsuarioAutenticado ator) {
        idempotenciaLock.bloquear(chave);
        var pedido = bloquearPedido(pedidoId);
        PedidoAccessPolicy.exigirPagamento(ator, pedido.escopo());
        var existente = pagamentos.findByPedidoId(pedidoId);
        if (existente.isPresent()) {
            var pagamento = existente.get();
            if (!chave.equals(pagamento.getChaveIdempotencia())
                    || !cenario.name().equals(pagamento.getCenarioMock())) {
                throw new ConflitoException("PAGAMENTO_JA_SOLICITADO", "Pedido já possui tentativa de pagamento.");
            }
            return new Inicio(null, resposta(pedido, pagamento), false);
        }
        if (pagamentos.findByChaveIdempotencia(chave).isPresent()) {
            throw new ConflitoException("IDEMPOTENCIA_CONFLITO", "Chave já usada em outro pedido.");
        }
        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO) {
            throw new ConflitoException("TRANSICAO_INVALIDA", "Pedido não aguarda pagamento.");
        }
        if (pedido.getReservaExpiraEm() != null && !pedido.getReservaExpiraEm().isAfter(Instant.now())) {
            throw new ConflitoException("RESERVA_EXPIRADA", "Reserva vencida; aguarde o cancelamento automático.");
        }
        String payload = "{\"pedidoId\":" + pedidoId + ",\"valor\":" + pedido.getTotal().toPlainString()
                + ",\"moeda\":\"BRL\",\"cenarioMock\":\"" + cenario + "\"}";
        var pagamento = pagamentos.saveAndFlush(new PagamentoEntity(pedidoId, chave, cenario.name(),
                pedido.getTotal(), payload));
        return new Inicio(new PaymentGateway.Solicitacao(pedidoId, pedido.getTotal(), chave, cenario),
                null, true);
    }

    private Inicio prepararReconciliacao(Long pedidoId, UsuarioAutenticado ator) {
        var pedido = bloquearPedido(pedidoId);
        exigirGerencia(ator, pedido);
        var pagamento = pagamentos.findByPedidoId(pedidoId).orElseThrow(() ->
                new RecursoNaoEncontradoException("PAGAMENTO_NAO_ENCONTRADO", "Tentativa de pagamento não encontrada."));
        if (pagamento.getStatus() == StatusPagamento.APROVADO
                || pagamento.getStatus() == StatusPagamento.RECUSADO
                || pagamento.getStatus() == StatusPagamento.FALHA_TECNICA) {
            return new Inicio(null, resposta(pedido, pagamento), false);
        }
        return new Inicio(new PaymentGateway.Solicitacao(pedidoId, pagamento.getValor(),
                pagamento.getChaveIdempotencia(), cenario(pagamento.getCenarioMock())), null, true);
    }

    private Resposta concluir(Long pedidoId, PaymentGateway.Resultado resultado, String operacao,
                             Long atorId, UUID requestId) {
        var pedido = bloquearPedido(pedidoId);
        var pagamento = pagamentos.findByPedidoId(pedidoId).orElseThrow(() ->
                new RecursoNaoEncontradoException("PAGAMENTO_NAO_ENCONTRADO", "Tentativa de pagamento não encontrada."));
        if (pagamento.getStatus() == StatusPagamento.APROVADO || pagamento.getStatus() == StatusPagamento.RECUSADO) {
            return resposta(pedido, pagamento);
        }
        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO
                && pedido.getStatus() != StatusPedido.PAGAMENTO_INDETERMINADO) {
            throw new ConflitoException("TRANSICAO_INVALIDA", "Estado do pedido incompatível com pagamento.");
        }
        var quantidades = itens.findByPedidoIdOrderById(pedidoId).stream()
                .collect(Collectors.toMap(i -> i.getProdutoId(), i -> i.getQuantidade()));
        switch (resultado.status()) {
            case APROVADO -> {
                estoque.baixar(pedido.getUnidadeId(), quantidades, pedidoId, atorId);
                pedido.mudarStatus(StatusPedido.EM_PREPARO);
            }
            case RECUSADO -> {
                estoque.liberar(pedido.getUnidadeId(), quantidades, pedidoId, atorId);
                pedido.mudarStatus(StatusPedido.PAGAMENTO_RECUSADO);
                fidelidade.devolverResgateSeAplicavel(pedido, atorId, requestId);
            }
            case INDETERMINADO -> pedido.mudarStatus(StatusPedido.PAGAMENTO_INDETERMINADO);
            default -> throw new IllegalArgumentException("Resultado do gateway inválido.");
        }
        pagamento.registrarResposta(resultado.status(), resultado.codigo(), resultado.payload());
        eventos.save(new PagamentoEventoEntity(pagamento.getId(), operacao, resultado.status(),
                resultado.codigo(), resultado.payload()));
        auditoria.registrar(atorId, pedido.getClienteId(), pedidoId, pedido.getUnidadeId(),
                "RESULTADO_PAGAMENTO", resultado.status().name(), "PAGAMENTO", requestId);
        return resposta(pedido, pagamento, resultado.mensagem());
    }

    private Resposta resposta(PedidoEntity pedido, PagamentoEntity pagamento) {
        String mensagem = switch (pagamento.getStatus()) {
            case APROVADO -> "Pagamento simulado aprovado.";
            case RECUSADO -> "Pagamento simulado recusado.";
            case PENDENTE -> "Tentativa registrada; processamento em andamento.";
            default -> "Resultado indeterminado; reserva retida até reconciliação.";
        };
        return resposta(pedido, pagamento, mensagem);
    }

    private Resposta resposta(PedidoEntity pedido, PagamentoEntity pagamento, String mensagem) {
        return new Resposta(pagamento.getId(), pedido.getId(), pagamento.getStatus(), pedido.getStatus(),
                pagamento.getValor(), mensagem, pagamento.getCodigoMock(),
                pagamento.getPayloadRetorno(), pagamento.getCriadoEm(), pagamento.getRespondidoEm());
    }

    private PedidoEntity bloquearPedido(Long pedidoId) {
        return pedidos.bloquear(pedidoId).orElseThrow(() ->
                new RecursoNaoEncontradoException("PEDIDO_NAO_ENCONTRADO", "Pedido não encontrado."));
    }

    private void exigirGerencia(UsuarioAutenticado ator, PedidoEntity pedido) {
        if (ator.perfil() == Perfil.ADMIN) return;
        if (ator.perfil() != Perfil.GERENTE || !Objects.equals(ator.unidadeId(), pedido.getUnidadeId())) {
            throw new AcessoNegadoException();
        }
    }

    private CenarioMock cenario(String texto) {
        try {
            return CenarioMock.valueOf(texto);
        } catch (RuntimeException ex) {
            throw new ValidacaoException("CENARIO_MOCK_INVALIDO", "Cenário mock obrigatório ou inválido.");
        }
    }

    private PaymentGateway.Resultado chamarGateway(java.util.function.Supplier<PaymentGateway.Resultado> chamada) {
        try {
            return chamada.get();
        } catch (RuntimeException ex) {
            return new PaymentGateway.Resultado(StatusPagamento.INDETERMINADO, "MOCK-UNKNOWN",
                    "Resultado indeterminado; reserva retida até reconciliação.",
                    "{\"gateway\":\"mock\",\"estado\":\"INDETERMINADO\",\"erro\":\"SEM_RESPOSTA\"}");
        }
    }

    private void exigirSemTransacao() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Gateway não pode ser chamado com transação de banco aberta.");
        }
    }

    private record Inicio(PaymentGateway.Solicitacao solicitacao, Resposta respostaExistente, boolean chamarGateway) { }
    public record Resposta(Long pagamentoId, Long pedidoId, StatusPagamento statusPagamento,
                          StatusPedido statusPedido, BigDecimal valor, String mensagem, String codigoMock,
                          String payloadGateway, Instant criadoEm, Instant respondidoEm) {
        public boolean indeterminado() {
            return statusPagamento == StatusPagamento.INDETERMINADO || statusPagamento == StatusPagamento.PENDENTE;
        }
    }
}
