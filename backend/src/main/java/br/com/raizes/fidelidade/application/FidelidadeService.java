package br.com.raizes.fidelidade.application;

import br.com.raizes.auditoria.application.RegistrarAuditoriaService;
import br.com.raizes.fidelidade.domain.RegraFidelidade;
import br.com.raizes.fidelidade.domain.TipoMovimentoFidelidade;
import br.com.raizes.fidelidade.infrastructure.jpa.FidelidadeContaEntity;
import br.com.raizes.fidelidade.infrastructure.jpa.FidelidadeContaJpaRepository;
import br.com.raizes.fidelidade.infrastructure.jpa.FidelidadeMovimentoEntity;
import br.com.raizes.fidelidade.infrastructure.jpa.FidelidadeMovimentoJpaRepository;
import br.com.raizes.identidade.domain.Perfil;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.identidade.infrastructure.jpa.ConsentimentoEntity;
import br.com.raizes.identidade.infrastructure.jpa.ConsentimentoJpaRepository;
import br.com.raizes.pedidos.infrastructure.jpa.PedidoEntity;
import br.com.raizes.shared.domain.AcessoNegadoException;
import br.com.raizes.shared.domain.ConflitoException;
import br.com.raizes.shared.domain.ValidacaoException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FidelidadeService {
    private final FidelidadeContaJpaRepository contas;
    private final FidelidadeMovimentoJpaRepository movimentos;
    private final ConsentimentoJpaRepository consentimentos;
    private final RegistrarAuditoriaService auditoria;

    public FidelidadeService(FidelidadeContaJpaRepository contas, FidelidadeMovimentoJpaRepository movimentos,
                             ConsentimentoJpaRepository consentimentos, RegistrarAuditoriaService auditoria) {
        this.contas = contas;
        this.movimentos = movimentos;
        this.consentimentos = consentimentos;
        this.auditoria = auditoria;
    }

    public boolean consentimentoAtivo(Long usuarioId) {
        if (usuarioId == null) return false;
        return consentimentos.findFirstByUsuarioIdAndFinalidadeOrderByIdDesc(usuarioId, "FIDELIDADE")
                .map(ConsentimentoEntity::isConsentido).orElse(false);
    }

    /**
     * Valida elegibilidade e debita pontos no momento de criar o pedido.
     * Pedido sem cliente ou sem consentimento vigente não participa e não pode solicitar resgate.
     */
    @Transactional
    public PreparacaoPedido prepararCriacao(Long clienteId, BigDecimal subtotal, int pontosSolicitados) {
        RegraFidelidade.exigirFormato(pontosSolicitados);
        boolean participa = consentimentoAtivo(clienteId);
        if (pontosSolicitados == 0) {
            return new PreparacaoPedido(participa, 0, BigDecimal.ZERO, subtotal);
        }
        if (clienteId == null) {
            throw new ConflitoException("FIDELIDADE_EXIGE_CLIENTE",
                    "Pedido anônimo não participa do programa de fidelidade.");
        }
        if (!participa) {
            throw new ConflitoException("CONSENTIMENTO_FIDELIDADE_INATIVO",
                    "Consentimento de fidelidade ativo é obrigatório para participar e resgatar pontos.");
        }
        RegraFidelidade.exigirDentroDoLimite(pontosSolicitados, subtotal);
        BigDecimal desconto = RegraFidelidade.desconto(pontosSolicitados);
        return new PreparacaoPedido(true, pontosSolicitados, desconto, subtotal.subtract(desconto));
    }

    @Transactional
    public void registrarResgate(Long clienteId, Long pedidoId, Long unidadeId, int pontos,
                                 Long atorId, UUID requestId) {
        if (pontos == 0) return;
        var conta = bloquearConta(clienteId);
        if (conta.getSaldo() < pontos) {
            throw new ConflitoException("SALDO_PONTOS_INSUFICIENTE", "Saldo de pontos insuficiente para o resgate.");
        }
        conta.debitar(pontos);
        movimentos.save(new FidelidadeMovimentoEntity(clienteId, pedidoId, atorId,
                TipoMovimentoFidelidade.RESGATE, pontos, conta.getSaldo()));
        auditoria.registrar(atorId, clienteId, pedidoId, unidadeId,
                "RESGATE_FIDELIDADE", "SUCESSO", "FIDELIDADE", requestId);
    }

    /**
     * Devolve os pontos resgatados se o pedido terminar em recusa, cancelamento ou expiração.
     * Idempotente por pedido: se já houve estorno ou não houve resgate, não altera o saldo.
     */
    @Transactional
    public int devolverResgateSeAplicavel(PedidoEntity pedido, Long atorId, UUID requestId) {
        int pontos = pedido.getPontosResgatados();
        if (pontos <= 0 || pedido.getClienteId() == null) return 0;
        var conta = bloquearConta(pedido.getClienteId());
        if (movimentos.existsByPedidoIdAndTipo(pedido.getId(), TipoMovimentoFidelidade.ESTORNO_RESGATE)) {
            return 0;
        }
        conta.creditar(pontos);
        movimentos.save(new FidelidadeMovimentoEntity(pedido.getClienteId(), pedido.getId(), atorId,
                TipoMovimentoFidelidade.ESTORNO_RESGATE, pontos, conta.getSaldo()));
        auditoria.registrar(atorId, pedido.getClienteId(), pedido.getId(), pedido.getUnidadeId(),
                "ESTORNO_RESGATE_FIDELIDADE", "SUCESSO", "FIDELIDADE", requestId);
        return pontos;
    }

    /**
     * Credita pontos quando o pedido chega a ENTREGUE, somente se o pedido participava e o consentimento
     * do cliente continua ativo na entrega. Protegido contra crédito duplicado por lock + constraint UNIQUE.
     */
    @Transactional
    public int acumularNaEntregaSeAplicavel(PedidoEntity pedido, Long atorId, UUID requestId) {
        Long clienteId = pedido.getClienteId();
        if (clienteId == null || !pedido.isParticipaFidelidade() || !consentimentoAtivo(clienteId)) {
            return 0;
        }
        int pontos = RegraFidelidade.pontosAcumulados(pedido.getTotal());
        if (pontos <= 0) return 0;
        var conta = bloquearConta(clienteId);
        var existente = movimentos.findByPedidoIdAndTipo(pedido.getId(), TipoMovimentoFidelidade.ACUMULO);
        if (existente.isPresent()) {
            return 0;
        }
        conta.creditar(pontos);
        movimentos.save(new FidelidadeMovimentoEntity(clienteId, pedido.getId(), atorId,
                TipoMovimentoFidelidade.ACUMULO, pontos, conta.getSaldo()));
        auditoria.registrar(atorId, clienteId, pedido.getId(), pedido.getUnidadeId(),
                "ACUMULO_FIDELIDADE", "SUCESSO", "FIDELIDADE", requestId);
        return pontos;
    }

    @Transactional
    public ResumoSaldo consultarSaldo(UsuarioAutenticado principal, UUID requestId) {
        exigirCliente(principal);
        boolean consentido = consentimentoAtivo(principal.id());
        int saldo = contas.findById(principal.id()).map(FidelidadeContaEntity::getSaldo).orElse(0);
        int acumulados = 0;
        int resgatados = 0;
        int estornados = 0;
        for (Object[] linha : movimentos.totaisPorTipo(principal.id())) {
            TipoMovimentoFidelidade tipo = (TipoMovimentoFidelidade) linha[0];
            int soma = ((Number) linha[1]).intValue();
            switch (tipo) {
                case ACUMULO -> acumulados = soma;
                case RESGATE -> resgatados = soma;
                case ESTORNO_RESGATE -> estornados = soma;
            }
        }
        int saldoPorMovimentos = acumulados - resgatados + estornados;
        auditoria.registrar(principal.id(), principal.id(), null,
                "ACESSO_DADO_PESSOAL", "SUCESSO", "FIDELIDADE", requestId);
        return new ResumoSaldo(principal.id(), consentido, saldo, acumulados, resgatados, estornados,
                saldoPorMovimentos, saldo == saldoPorMovimentos,
                new RegrasVigentes(RegraFidelidade.PONTOS_POR_REAL, RegraFidelidade.BLOCO_RESGATE,
                        RegraFidelidade.VALOR_BLOCO, RegraFidelidade.LIMITE_PERCENTUAL_DESCONTO));
    }

    @Transactional
    public Page<MovimentoView> listarMovimentos(UsuarioAutenticado principal, int page, int limit, UUID requestId) {
        exigirCliente(principal);
        if (page < 1 || limit < 1 || limit > 100) {
            throw new ValidacaoException("PAGINACAO_INVALIDA", "page deve ser positivo e limit entre 1 e 100.");
        }
        auditoria.registrar(principal.id(), principal.id(), null,
                "ACESSO_DADO_PESSOAL", "SUCESSO", "FIDELIDADE_HISTORICO", requestId);
        var pageable = PageRequest.of(page - 1, limit,
                Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id")));
        return movimentos.findByUsuarioId(principal.id(), pageable)
                .map(m -> new MovimentoView(m.getId(), m.getPedidoId(), m.getTipo(), m.getPontos(),
                        m.getTipo().variacao(m.getPontos()), m.getSaldoApos(), m.getCriadoEm()));
    }

    private FidelidadeContaEntity bloquearConta(Long usuarioId) {
        contas.criarSeAusente(usuarioId);
        return contas.bloquear(usuarioId).orElseThrow();
    }

    private static void exigirCliente(UsuarioAutenticado principal) {
        if (principal == null || principal.perfil() != Perfil.CLIENTE) {
            throw new AcessoNegadoException();
        }
    }

    public record PreparacaoPedido(boolean participaFidelidade, int pontosResgatados,
                                   BigDecimal descontoFidelidade, BigDecimal totalAPagar) { }

    public record RegrasVigentes(int pontosPorRealEntregue, int blocoResgatePontos,
                                 BigDecimal descontoPorBloco, BigDecimal limitePercentualSubtotal) { }

    public record ResumoSaldo(Long clienteId, boolean consentimentoAtivo, int saldo,
                              int totalAcumulado, int totalResgatado, int totalEstornado,
                              int saldoCalculadoMovimentos, boolean saldoConsistente,
                              RegrasVigentes regras) { }

    public record MovimentoView(Long movimentoId, Long pedidoId, TipoMovimentoFidelidade tipo,
                                int pontos, int variacaoSaldo, int saldoApos, Instant criadoEm) { }
}

