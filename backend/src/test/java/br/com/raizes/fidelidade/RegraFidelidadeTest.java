package br.com.raizes.fidelidade;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.raizes.fidelidade.domain.RegraFidelidade;
import br.com.raizes.fidelidade.domain.TipoMovimentoFidelidade;
import br.com.raizes.shared.domain.ConflitoException;
import br.com.raizes.shared.domain.ValidacaoException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RegraFidelidadeTest {
    @Test
    void calculaPontosInteirosPorRealPagoEDescontoPorBlocoDeCem() {
        assertEquals(0, RegraFidelidade.pontosAcumulados(null));
        assertEquals(0, RegraFidelidade.pontosAcumulados(BigDecimal.ZERO));
        assertEquals(0, RegraFidelidade.pontosAcumulados(new BigDecimal("0.99")));
        assertEquals(39, RegraFidelidade.pontosAcumulados(new BigDecimal("39.90")));
        assertEquals(119, RegraFidelidade.pontosAcumulados(new BigDecimal("119.70")));

        assertEquals(0, new BigDecimal("0.00").compareTo(RegraFidelidade.desconto(0)));
        assertEquals(0, new BigDecimal("5.00").compareTo(RegraFidelidade.desconto(100)));
        assertEquals(0, new BigDecimal("15.00").compareTo(RegraFidelidade.desconto(300)));

        assertEquals(100, TipoMovimentoFidelidade.ACUMULO.variacao(100));
        assertEquals(-100, TipoMovimentoFidelidade.RESGATE.variacao(100));
        assertEquals(100, TipoMovimentoFidelidade.ESTORNO_RESGATE.variacao(100));
    }

    @Test
    void validaMultiploDeCemELimiteDeCinquentaPorCentoDoSubtotal() {
        // Subtotal 39.90 -> 50% = 19.95 -> máximo 3 blocos de R$ 5,00 = 300 pontos
        assertEquals(300, RegraFidelidade.limitePontos(new BigDecimal("39.90")));
        assertDoesNotThrow(() -> RegraFidelidade.exigirDentroDoLimite(300, new BigDecimal("39.90")));

        var formatoInvalido = assertThrows(ValidacaoException.class,
                () -> RegraFidelidade.exigirFormato(50));
        assertEquals("PONTOS_RESGATE_INVALIDOS", formatoInvalido.code());

        var negativo = assertThrows(ValidacaoException.class,
                () -> RegraFidelidade.exigirFormato(-100));
        assertEquals("PONTOS_RESGATE_INVALIDOS", negativo.code());

        var acimaDoLimite = assertThrows(ConflitoException.class,
                () -> RegraFidelidade.exigirDentroDoLimite(400, new BigDecimal("39.90")));
        assertEquals("LIMITE_RESGATE_EXCEDIDO", acimaDoLimite.code());
    }
}

