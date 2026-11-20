package br.com.raizes.pedidos.application;

import br.com.raizes.identidade.domain.Perfil;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.pedidos.domain.CanalPedido;
import br.com.raizes.pedidos.domain.StatusPedido;
import br.com.raizes.pedidos.infrastructure.jpa.PedidoJpaRepository;
import br.com.raizes.shared.domain.AcessoNegadoException;
import br.com.raizes.shared.domain.ValidacaoException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RelatorioPedidosService {
    private final PedidoJpaRepository pedidos;

    public RelatorioPedidosService(PedidoJpaRepository pedidos) {
        this.pedidos = pedidos;
    }

    @Transactional(readOnly = true)
    public Relatorio consultar(Long unidadeId, String canalPedido, String status, UsuarioAutenticado principal) {
        if (principal == null || (principal.perfil() != Perfil.GERENTE && principal.perfil() != Perfil.ADMIN)) {
            throw new AcessoNegadoException();
        }
        if (unidadeId != null && unidadeId < 1) {
            throw new ValidacaoException("UNIDADE_INVALIDA", "unidadeId deve ser positivo.");
        }
        CanalPedido canal = opcional(CanalPedido.class, canalPedido, "CANAL_PEDIDO_INVALIDO");
        StatusPedido estado = opcional(StatusPedido.class, status, "STATUS_PEDIDO_INVALIDO");
        Long unidadeEscopo;
        if (principal.perfil() == Perfil.GERENTE) {
            if (principal.unidadeId() == null || (unidadeId != null && !Objects.equals(unidadeId, principal.unidadeId()))) {
                throw new AcessoNegadoException();
            }
            unidadeEscopo = principal.unidadeId();
        } else {
            unidadeEscopo = unidadeId;
        }
        List<ItemUnidadeCanal> itens = pedidos.agregarPorUnidadeECanal(unidadeEscopo, canal, estado).stream()
                .map(r -> new ItemUnidadeCanal(
                        ((Number) r[0]).longValue(),
                        (CanalPedido) r[1],
                        ((Number) r[2]).longValue(),
                        (BigDecimal) r[3],
                        (BigDecimal) r[4],
                        (BigDecimal) r[5],
                        ((Number) r[6]).longValue()))
                .toList();
        long totalPedidos = itens.stream().mapToLong(ItemUnidadeCanal::quantidadePedidos).sum();
        BigDecimal subtotalBruto = itens.stream().map(ItemUnidadeCanal::subtotalItens)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descontoFidelidade = itens.stream().map(ItemUnidadeCanal::descontoFidelidade)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal valorTotal = itens.stream().map(ItemUnidadeCanal::valorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long pontosResgatados = itens.stream().mapToLong(ItemUnidadeCanal::pontosResgatados).sum();
        return new Relatorio(unidadeEscopo, canal, estado, totalPedidos,
                subtotalBruto, descontoFidelidade, valorTotal, pontosResgatados, itens);
    }

    private static <E extends Enum<E>> E opcional(Class<E> tipo, String valor, String codigo) {
        if (valor == null) return null;
        try {
            return Enum.valueOf(tipo, valor);
        } catch (RuntimeException ex) {
            throw new ValidacaoException(codigo, "Filtro inválido.");
        }
    }

    public record ItemUnidadeCanal(Long unidadeId, CanalPedido canalPedido, long quantidadePedidos,
                                   BigDecimal subtotalItens, BigDecimal descontoFidelidade,
                                   BigDecimal valorTotal, long pontosResgatados) { }

    public record Relatorio(Long unidadeFiltro, CanalPedido canalFiltro, StatusPedido statusFiltro,
                            long totalPedidos, BigDecimal subtotalItensTotal,
                            BigDecimal descontoFidelidadeTotal, BigDecimal valorTotal,
                            long pontosResgatadosTotal, List<ItemUnidadeCanal> itens) { }
}

