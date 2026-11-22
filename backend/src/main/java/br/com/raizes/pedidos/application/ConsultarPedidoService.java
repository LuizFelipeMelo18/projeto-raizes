package br.com.raizes.pedidos.application;

import br.com.raizes.auditoria.application.RegistrarAuditoriaService;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.pagamentos.domain.StatusPagamento;
import br.com.raizes.pagamentos.infrastructure.jpa.PagamentoJpaRepository;
import br.com.raizes.pedidos.domain.CanalPedido;
import br.com.raizes.pedidos.domain.ModalidadeAtendimento;
import br.com.raizes.pedidos.domain.PedidoAccessPolicy;
import br.com.raizes.pedidos.domain.StatusPedido;
import br.com.raizes.pedidos.infrastructure.jpa.ItemPedidoEntity;
import br.com.raizes.pedidos.infrastructure.jpa.ItemPedidoJpaRepository;
import br.com.raizes.pedidos.infrastructure.jpa.PedidoJpaRepository;
import br.com.raizes.shared.domain.RecursoNaoEncontradoException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarPedidoService {
    private final PedidoJpaRepository pedidos;
    private final ItemPedidoJpaRepository itens;
    private final PagamentoJpaRepository pagamentos;
    private final RegistrarAuditoriaService auditoria;

    public ConsultarPedidoService(PedidoJpaRepository pedidos, ItemPedidoJpaRepository itens,
                                  PagamentoJpaRepository pagamentos, RegistrarAuditoriaService auditoria) {
        this.pedidos = pedidos;
        this.itens = itens;
        this.pagamentos = pagamentos;
        this.auditoria = auditoria;
    }

    @Transactional
    public PedidoDetalhe consultar(Long id, UsuarioAutenticado ator, UUID requestId) {
        var pedido = pedidos.findById(id).orElseThrow(() ->
                new RecursoNaoEncontradoException("PEDIDO_NAO_ENCONTRADO", "Pedido não encontrado."));
        PedidoAccessPolicy.exigirConsulta(ator, pedido.escopo());
        List<Item> produtos = itens.findByPedidoIdOrderById(id).stream().map(this::mapearItem).toList();
        var pagamento = pagamentos.findByPedidoId(id)
                .map(p -> new Pagamento(p.getStatus(), p.getValor())).orElse(null);
        auditoria.registrar(ator.id(), pedido.getClienteId(), id, pedido.getUnidadeId(),
                "ACESSO_DADO_PESSOAL", "SUCESSO", "PEDIDO", requestId);
        return new PedidoDetalhe(pedido.getId(), pedido.getUnidadeId(), pedido.getCanalPedido(),
                pedido.getModalidadeAtendimento(), pedido.getStatus(), pedido.getSubtotalItens(),
                pedido.getDescontoFidelidade(), pedido.getPontosResgatados(), pedido.isParticipaFidelidade(),
                pedido.getTotal(), produtos, pagamento, pedido.getMotivoCancelamento(), pedido.getCriadoEm());
    }

    private Item mapearItem(ItemPedidoEntity item) {
        return new Item(item.getProdutoId(), item.getQuantidade(), item.getPrecoUnitario(), item.getSubtotal());
    }

    public record Item(Long produtoId, int quantidade, BigDecimal precoUnitario, BigDecimal subtotal) { }
    public record Pagamento(StatusPagamento status, BigDecimal valor) { }
    public record PedidoDetalhe(Long pedidoId, Long unidadeId, CanalPedido canalPedido,
                                ModalidadeAtendimento modalidadeAtendimento, StatusPedido status,
                                BigDecimal subtotalItens, BigDecimal descontoFidelidade,
                                int pontosResgatados, boolean participaFidelidade,
                                BigDecimal total, List<Item> itens, Pagamento pagamento,
                                String motivoCancelamento, Instant criadoEm) { }
}
