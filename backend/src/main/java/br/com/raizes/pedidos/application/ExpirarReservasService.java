package br.com.raizes.pedidos.application;

import br.com.raizes.auditoria.application.RegistrarAuditoriaService;
import br.com.raizes.estoque.application.ReservaEstoqueService;
import br.com.raizes.fidelidade.application.FidelidadeService;
import br.com.raizes.pagamentos.infrastructure.jpa.PagamentoJpaRepository;
import br.com.raizes.pedidos.domain.StatusPedido;
import br.com.raizes.pedidos.infrastructure.jpa.ItemPedidoJpaRepository;
import br.com.raizes.pedidos.infrastructure.jpa.PedidoJpaRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExpirarReservasService {
    private final PedidoJpaRepository pedidos;
    private final PagamentoJpaRepository pagamentos;
    private final ItemPedidoJpaRepository itens;
    private final ReservaEstoqueService estoque;
    private final FidelidadeService fidelidade;
    private final RegistrarAuditoriaService auditoria;
    private final TransactionTemplate transacao;

    public ExpirarReservasService(PedidoJpaRepository pedidos, PagamentoJpaRepository pagamentos,
                                  ItemPedidoJpaRepository itens, ReservaEstoqueService estoque,
                                  FidelidadeService fidelidade, RegistrarAuditoriaService auditoria,
                                  PlatformTransactionManager gerenciador) {
        this.pedidos = pedidos;
        this.pagamentos = pagamentos;
        this.itens = itens;
        this.estoque = estoque;
        this.fidelidade = fidelidade;
        this.auditoria = auditoria;
        this.transacao = new TransactionTemplate(gerenciador);
    }

    @Scheduled(fixedDelayString = "${raizes.pedidos.expiracao.intervalo-ms:60000}")
    public void expirarAgendado() {
        expirarAte(Instant.now());
    }

    public int expirarAte(Instant agora) {
        int total = 0;
        for (Long id : pedidos.expirados(agora, PageRequest.of(0, 100))) {
            Boolean expirou = transacao.execute(status -> expirarUm(id, agora));
            if (Boolean.TRUE.equals(expirou)) total++;
        }
        return total;
    }

    private boolean expirarUm(Long id, Instant agora) {
        var pedido = pedidos.bloquear(id).orElse(null);
        if (pedido == null || pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO
                || pedido.getReservaExpiraEm() == null || pedido.getReservaExpiraEm().isAfter(agora)
                || pagamentos.findByPedidoId(id).isPresent()) return false;
        var quantidades = itens.findByPedidoIdOrderById(id).stream()
                .collect(Collectors.toMap(i -> i.getProdutoId(), i -> i.getQuantidade()));
        estoque.liberar(pedido.getUnidadeId(), quantidades, id, null);
        pedido.mudarStatus(StatusPedido.CANCELADO);
        UUID requestId = UUID.randomUUID();
        fidelidade.devolverResgateSeAplicavel(pedido, null, requestId);
        auditoria.registrar(null, pedido.getClienteId(), id, pedido.getUnidadeId(),
                "EXPIRACAO_RESERVA", "SUCESSO", "PEDIDO", requestId);
        return true;
    }
}
