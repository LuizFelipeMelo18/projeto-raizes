package br.com.raizes.estoque.application;

import br.com.raizes.estoque.domain.TipoMovimentoEstoque;
import br.com.raizes.estoque.infrastructure.jpa.EstoqueSaldoEntity;
import br.com.raizes.estoque.infrastructure.jpa.EstoqueSaldoId;
import br.com.raizes.estoque.infrastructure.jpa.EstoqueSaldoJpaRepository;
import br.com.raizes.estoque.infrastructure.jpa.MovimentoEstoqueEntity;
import br.com.raizes.estoque.infrastructure.jpa.MovimentoEstoqueJpaRepository;
import br.com.raizes.pedidos.infrastructure.jpa.PedidoJpaRepository;
import br.com.raizes.shared.domain.ConflitoException;
import br.com.raizes.shared.domain.RecursoNaoEncontradoException;
import br.com.raizes.shared.domain.ValidacaoException;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservaEstoqueService {
    private final EstoqueSaldoJpaRepository saldos;
    private final MovimentoEstoqueJpaRepository movimentos;
    private final PedidoJpaRepository pedidos;

    public ReservaEstoqueService(EstoqueSaldoJpaRepository saldos, MovimentoEstoqueJpaRepository movimentos,
                                 PedidoJpaRepository pedidos) {
        this.saldos = saldos;
        this.movimentos = movimentos;
        this.pedidos = pedidos;
    }

    @Transactional
    public void reservar(Long unidadeId, Map<Long, Integer> quantidades, Long pedidoId, Long atorId) {
        aplicar(unidadeId, quantidades, pedidoId, atorId, TipoMovimentoEstoque.RESERVA);
    }

    @Transactional
    public void liberar(Long unidadeId, Map<Long, Integer> quantidades, Long pedidoId, Long atorId) {
        aplicar(unidadeId, quantidades, pedidoId, atorId, TipoMovimentoEstoque.LIBERACAO);
    }

    @Transactional
    public void baixar(Long unidadeId, Map<Long, Integer> quantidades, Long pedidoId, Long atorId) {
        aplicar(unidadeId, quantidades, pedidoId, atorId, TipoMovimentoEstoque.BAIXA);
    }

    private void aplicar(Long unidadeId, Map<Long, Integer> quantidades, Long pedidoId, Long atorId,
                         TipoMovimentoEstoque tipo) {
        if (unidadeId == null || unidadeId < 1 || pedidoId == null || pedidoId < 1
                || (atorId != null && atorId < 1) || quantidades == null || quantidades.isEmpty()) {
            throw new ValidacaoException("RESERVA_INVALIDA", "Pedido, unidade e itens são obrigatórios.");
        }
        var pedido = pedidos.findById(pedidoId).orElseThrow(() ->
                new RecursoNaoEncontradoException("PEDIDO_NAO_ENCONTRADO", "Pedido não encontrado."));
        if (!Objects.equals(pedido.getUnidadeId(), unidadeId)) {
            throw new ConflitoException("PEDIDO_UNIDADE_DIVERGENTE", "Pedido pertence a outra unidade.");
        }
        for (var entrada : quantidades.entrySet()) {
            Long produtoId = entrada.getKey();
            Integer quantidade = entrada.getValue();
            if (produtoId == null || produtoId < 1 || quantidade == null || quantidade < 1) {
                throw new ValidacaoException("RESERVA_INVALIDA", "Produtos e quantidades devem ser positivos.");
            }
        }
        // Ordem estável de bloqueio impede inversão de locks entre pedidos concorrentes.
        for (Long produtoId : quantidades.keySet().stream().sorted().toList()) {
            int quantidade = quantidades.get(produtoId);
            EstoqueSaldoEntity saldo = saldos.bloquear(new EstoqueSaldoId(unidadeId, produtoId))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("ESTOQUE_NAO_ENCONTRADO",
                            "Produto fora do estoque da unidade."));
            switch (tipo) {
                case RESERVA -> saldo.reservar(quantidade);
                case LIBERACAO -> saldo.liberar(quantidade);
                case BAIXA -> saldo.baixarReserva(quantidade);
                default -> throw new IllegalArgumentException("Tipo de reserva inválido.");
            }
            movimentos.save(new MovimentoEstoqueEntity(unidadeId, produtoId, pedidoId, atorId,
                    tipo, quantidade, null));
        }
    }
}
