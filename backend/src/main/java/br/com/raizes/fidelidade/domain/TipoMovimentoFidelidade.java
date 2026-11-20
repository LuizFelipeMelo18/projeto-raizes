package br.com.raizes.fidelidade.domain;

public enum TipoMovimentoFidelidade {
    /** Crédito após pedido ENTREGUE. */
    ACUMULO(1),
    /** Débito na criação de pedido com desconto. */
    RESGATE(-1),
    /** Devolução do resgate quando o pedido é recusado, cancelado ou expira. */
    ESTORNO_RESGATE(1);

    private final int sinal;

    TipoMovimentoFidelidade(int sinal) {
        this.sinal = sinal;
    }

    public int variacao(int pontos) {
        return sinal * pontos;
    }
}

