package br.com.raizes.pedidos.application;

import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.pedidos.domain.CanalPedido;
import br.com.raizes.pedidos.domain.ModalidadeAtendimento;
import br.com.raizes.pedidos.domain.StatusPedido;
import br.com.raizes.pedidos.infrastructure.jpa.PedidoJpaRepository;
import br.com.raizes.shared.domain.AcessoNegadoException;
import br.com.raizes.shared.domain.RecursoNaoEncontradoException;
import br.com.raizes.shared.domain.ValidacaoException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarPedidosService {
    private final PedidoJpaRepository pedidos;

    public ListarPedidosService(PedidoJpaRepository pedidos) {
        this.pedidos = pedidos;
    }

    @Transactional(readOnly = true)
    public Page<Resumo> listar(Long unidadeId, String canalPedido, String status, int page, int limit,
                              UsuarioAutenticado ator) {
        if (page < 1 || limit < 1 || limit > 100) {
            throw new ValidacaoException("PAGINACAO_INVALIDA", "page deve ser positivo e limit entre 1 e 100.");
        }
        if (unidadeId != null && unidadeId < 1) {
            throw new ValidacaoException("UNIDADE_INVALIDA", "unidadeId deve ser positivo.");
        }
        CanalPedido canal = opcional(CanalPedido.class, canalPedido, "CANAL_PEDIDO_INVALIDO");
        StatusPedido estado = opcional(StatusPedido.class, status, "STATUS_PEDIDO_INVALIDO");
        Long clienteId = null;
        Long unidadeEscopo;
        switch (ator.perfil()) {
            case CLIENTE -> {
                clienteId = ator.id();
                if (unidadeId != null) {
                    throw new AcessoNegadoException();
                }
                unidadeEscopo = null;
            }
            case ATENDENTE, COZINHA, GERENTE -> {
                if (ator.unidadeId() == null || (unidadeId != null && !Objects.equals(unidadeId, ator.unidadeId()))) {
                    throw new AcessoNegadoException();
                }
                unidadeEscopo = ator.unidadeId();
            }
            case ADMIN -> unidadeEscopo = unidadeId;
            default -> throw new AcessoNegadoException();
        }
        return pedidos.filtrar(clienteId, unidadeEscopo, canal, estado,
                PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id"))))
                .map(p -> new Resumo(p.getId(), p.getUnidadeId(), p.getCanalPedido(),
                        p.getModalidadeAtendimento(), p.getStatus(), p.getTotal(), p.getCriadoEm()));
    }

    @Transactional(readOnly = true)
    public Acompanhamento acompanhar(String codigo) {
        if (codigo == null || !codigo.matches("[A-Za-z0-9_-]{24}")) {
            throw new RecursoNaoEncontradoException("PEDIDO_NAO_ENCONTRADO", "Pedido não encontrado.");
        }
        var pedido = pedidos.findByCodigoAcompanhamento(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("PEDIDO_NAO_ENCONTRADO", "Pedido não encontrado."));
        return new Acompanhamento(pedido.getStatus(), pedido.getAtualizadoEm());
    }

    private static <E extends Enum<E>> E opcional(Class<E> tipo, String valor, String codigo) {
        if (valor == null) return null;
        try {
            return Enum.valueOf(tipo, valor);
        } catch (RuntimeException ex) {
            throw new ValidacaoException(codigo, "Filtro inválido.");
        }
    }

    public record Resumo(Long pedidoId, Long unidadeId, CanalPedido canalPedido,
                         ModalidadeAtendimento modalidadeAtendimento, StatusPedido status,
                         BigDecimal total, Instant criadoEm) { }
    public record Acompanhamento(StatusPedido status, Instant atualizadoEm) { }
}
