package br.com.raizes.pedidos.domain;

import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.shared.domain.AcessoNegadoException;
import br.com.raizes.shared.domain.RecursoNaoEncontradoException;
import java.util.Objects;

public final class PedidoAccessPolicy {
    private PedidoAccessPolicy() {
    }

    public static void exigirConsulta(UsuarioAutenticado ator, PedidoEscopo pedido) {
        switch (ator.perfil()) {
            case CLIENTE -> {
                if (!Objects.equals(ator.id(), pedido.clienteId())) {
                    throw new RecursoNaoEncontradoException("PEDIDO_NAO_ENCONTRADO", "Pedido não encontrado.");
                }
            }
            case ATENDENTE, COZINHA, GERENTE -> exigirMesmaUnidade(ator, pedido);
            case ADMIN -> { }
            case TOTEM -> throw new AcessoNegadoException();
        }
    }

    public static void exigirPagamento(UsuarioAutenticado ator, PedidoEscopo pedido) {
        switch (ator.perfil()) {
            case CLIENTE -> {
                if (!Objects.equals(ator.id(), pedido.clienteId())) {
                    throw new RecursoNaoEncontradoException("PEDIDO_NAO_ENCONTRADO", "Pedido não encontrado.");
                }
            }
            case TOTEM -> {
                if (!Objects.equals(ator.id(), pedido.criadoPorId())) {
                    throw new AcessoNegadoException();
                }
                exigirMesmaUnidade(ator, pedido);
            }
            case ATENDENTE -> exigirMesmaUnidade(ator, pedido);
            default -> throw new AcessoNegadoException();
        }
    }

    private static void exigirMesmaUnidade(UsuarioAutenticado ator, PedidoEscopo pedido) {
        if (ator.unidadeId() == null || !Objects.equals(ator.unidadeId(), pedido.unidadeId())) {
            throw new AcessoNegadoException();
        }
    }
}
