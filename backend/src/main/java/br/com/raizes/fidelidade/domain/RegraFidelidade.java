package br.com.raizes.fidelidade.domain;

import br.com.raizes.shared.domain.ConflitoException;
import br.com.raizes.shared.domain.ValidacaoException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Regra do programa de fidelidade do MVP.
 * <ul>
 *   <li>Acúmulo: 1 ponto por R$ 1,00 inteiro efetivamente pago (total após desconto), creditado quando o pedido
 *   chega a ENTREGUE.</li>
 *   <li>Resgate: blocos de 100 pontos; cada bloco vale R$ 5,00 de desconto (R$ 0,05 por ponto).</li>
 *   <li>Limite: o desconto não ultrapassa 50% do subtotal dos itens, portanto sempre resta valor a pagar.</li>
 * </ul>
 */
public final class RegraFidelidade {
    public static final int PONTOS_POR_REAL = 1;
    public static final int BLOCO_RESGATE = 100;
    public static final BigDecimal VALOR_BLOCO = new BigDecimal("5.00");
    public static final BigDecimal LIMITE_PERCENTUAL_DESCONTO = new BigDecimal("0.50");
    public static final int MAXIMO_PONTOS_POR_PEDIDO = 1_000_000;

    private RegraFidelidade() { }

    public static int pontosAcumulados(BigDecimal valorPago) {
        if (valorPago == null || valorPago.signum() <= 0) return 0;
        return valorPago.setScale(0, RoundingMode.DOWN).intValueExact() * PONTOS_POR_REAL;
    }

    public static BigDecimal desconto(int pontos) {
        return VALOR_BLOCO.multiply(BigDecimal.valueOf(pontos / BLOCO_RESGATE)).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Maior quantidade de pontos aceita para o subtotal informado, sem considerar saldo. */
    public static int limitePontos(BigDecimal subtotal) {
        if (subtotal == null || subtotal.signum() <= 0) return 0;
        BigDecimal descontoMaximo = subtotal.multiply(LIMITE_PERCENTUAL_DESCONTO);
        int blocos = descontoMaximo.divide(VALOR_BLOCO, 0, RoundingMode.DOWN).intValue();
        return Math.min(blocos * BLOCO_RESGATE, MAXIMO_PONTOS_POR_PEDIDO);
    }

    public static void exigirFormato(int pontos) {
        if (pontos < 0 || pontos > MAXIMO_PONTOS_POR_PEDIDO || pontos % BLOCO_RESGATE != 0) {
            throw new ValidacaoException("PONTOS_RESGATE_INVALIDOS",
                    "pontosResgate deve ser zero ou múltiplo positivo de " + BLOCO_RESGATE + ".");
        }
    }

    public static void exigirDentroDoLimite(int pontos, BigDecimal subtotal) {
        exigirFormato(pontos);
        if (pontos > limitePontos(subtotal)) {
            throw new ConflitoException("LIMITE_RESGATE_EXCEDIDO",
                    "Desconto de fidelidade limitado a 50% do subtotal do pedido.");
        }
    }
}

