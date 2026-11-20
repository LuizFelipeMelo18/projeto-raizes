package br.com.raizes.pedidos;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.raizes.identidade.domain.Perfil;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.pedidos.domain.PedidoAccessPolicy;
import br.com.raizes.pedidos.domain.PedidoEscopo;
import br.com.raizes.pedidos.domain.PedidoStatusMachine;
import br.com.raizes.pedidos.domain.StatusPedido;
import br.com.raizes.shared.domain.AcessoNegadoException;
import br.com.raizes.shared.domain.ConflitoException;
import br.com.raizes.shared.domain.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;

class PedidoAccessPolicyTest {
    @Test
    void totemSoPodePagarPedidoQueCriouNaPropriaUnidade() {
        var totem = new UsuarioAutenticado(101L, Perfil.TOTEM, 1L);
        assertDoesNotThrow(() -> PedidoAccessPolicy.exigirPagamento(totem, new PedidoEscopo(1L, null, 101L)));
        assertThrows(AcessoNegadoException.class,
                () -> PedidoAccessPolicy.exigirPagamento(totem, new PedidoEscopo(2L, null, 101L)));
        assertThrows(AcessoNegadoException.class,
                () -> PedidoAccessPolicy.exigirPagamento(totem, new PedidoEscopo(1L, null, 102L)));
    }

    @Test
    void consultaDePedidoIsolaClienteDonoEquipeDaUnidadeEAdmin() {
        var pedidoUnidade1 = new PedidoEscopo(1L, 100L, 100L);
        assertDoesNotThrow(() -> PedidoAccessPolicy.exigirConsulta(
                new UsuarioAutenticado(100L, Perfil.CLIENTE, null), pedidoUnidade1));
        var outroCliente = assertThrows(RecursoNaoEncontradoException.class, () ->
                PedidoAccessPolicy.exigirConsulta(new UsuarioAutenticado(108L, Perfil.CLIENTE, null), pedidoUnidade1));
        assertEquals("PEDIDO_NAO_ENCONTRADO", outroCliente.code());

        assertDoesNotThrow(() -> PedidoAccessPolicy.exigirConsulta(
                new UsuarioAutenticado(102L, Perfil.ATENDENTE, 1L), pedidoUnidade1));
        assertDoesNotThrow(() -> PedidoAccessPolicy.exigirConsulta(
                new UsuarioAutenticado(103L, Perfil.COZINHA, 1L), pedidoUnidade1));
        assertDoesNotThrow(() -> PedidoAccessPolicy.exigirConsulta(
                new UsuarioAutenticado(104L, Perfil.GERENTE, 1L), pedidoUnidade1));
        assertDoesNotThrow(() -> PedidoAccessPolicy.exigirConsulta(
                new UsuarioAutenticado(105L, Perfil.ADMIN, null), pedidoUnidade1));

        assertThrows(AcessoNegadoException.class, () -> PedidoAccessPolicy.exigirConsulta(
                new UsuarioAutenticado(106L, Perfil.ATENDENTE, 2L), pedidoUnidade1));
        assertThrows(AcessoNegadoException.class, () -> PedidoAccessPolicy.exigirConsulta(
                new UsuarioAutenticado(101L, Perfil.TOTEM, 1L), pedidoUnidade1));
    }

    @Test
    void maquinaDeEstadosOperacionalAceitaSoEmPreparoParaProntoEProntoParaEntregue() {
        assertDoesNotThrow(() -> PedidoStatusMachine.exigirTransicaoOperacional(
                StatusPedido.EM_PREPARO, StatusPedido.PRONTO));
        assertDoesNotThrow(() -> PedidoStatusMachine.exigirTransicaoOperacional(
                StatusPedido.PRONTO, StatusPedido.ENTREGUE));

        for (StatusPedido origem : StatusPedido.values()) {
            for (StatusPedido destino : StatusPedido.values()) {
                boolean valida = (origem == StatusPedido.EM_PREPARO && destino == StatusPedido.PRONTO)
                        || (origem == StatusPedido.PRONTO && destino == StatusPedido.ENTREGUE);
                if (!valida) {
                    var ex = assertThrows(ConflitoException.class,
                            () -> PedidoStatusMachine.exigirTransicaoOperacional(origem, destino));
                    assertEquals("TRANSICAO_INVALIDA", ex.code());
                }
            }
        }
    }
}
