package br.com.raizes.pedidos.domain;

import br.com.raizes.shared.domain.ConflitoException;

public final class PedidoStatusMachine {
    private PedidoStatusMachine() { }

    public static void exigirTransicaoOperacional(StatusPedido atual, StatusPedido destino) {
        if ((atual == StatusPedido.EM_PREPARO && destino == StatusPedido.PRONTO)
                || (atual == StatusPedido.PRONTO && destino == StatusPedido.ENTREGUE)) return;
        throw new ConflitoException("TRANSICAO_INVALIDA", "Transição de estado não permitida.");
    }
}
