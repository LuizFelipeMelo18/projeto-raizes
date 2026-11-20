package br.com.raizes.estoque;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.raizes.estoque.domain.EstoqueUnidadePolicy;
import br.com.raizes.estoque.infrastructure.jpa.EstoqueSaldoEntity;
import br.com.raizes.identidade.domain.Perfil;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.shared.domain.AcessoNegadoException;
import br.com.raizes.shared.domain.ConflitoException;
import br.com.raizes.shared.domain.ValidacaoException;
import org.junit.jupiter.api.Test;

class EstoqueDomainTest {
    @Test
    void politicaDeGerenciaExigeGerenteDaMesmaUnidadeOuAdminGlobal() {
        assertDoesNotThrow(() -> EstoqueUnidadePolicy.exigirGerencia(
                new UsuarioAutenticado(104L, Perfil.GERENTE, 1L), 1L));
        assertDoesNotThrow(() -> EstoqueUnidadePolicy.exigirGerencia(
                new UsuarioAutenticado(105L, Perfil.ADMIN, null), 1L));
        assertDoesNotThrow(() -> EstoqueUnidadePolicy.exigirGerencia(
                new UsuarioAutenticado(105L, Perfil.ADMIN, null), 2L));

        assertThrows(AcessoNegadoException.class, () -> EstoqueUnidadePolicy.exigirGerencia(
                new UsuarioAutenticado(104L, Perfil.GERENTE, 1L), 2L));
        assertThrows(AcessoNegadoException.class, () -> EstoqueUnidadePolicy.exigirGerencia(
                new UsuarioAutenticado(102L, Perfil.ATENDENTE, 1L), 1L));
        assertThrows(AcessoNegadoException.class, () -> EstoqueUnidadePolicy.exigirGerencia(null, 1L));
    }

    @Test
    void invariantesDeSaldoImpedemDisponivelNegativoEReservaAcimaDoFisico() throws Exception {
        var ctor = EstoqueSaldoEntity.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        EstoqueSaldoEntity saldo = ctor.newInstance();

        saldo.registrarEntrada(5);
        assertEquals(5, saldo.getFisico());
        assertEquals(0, saldo.getReservado());
        assertEquals(5, saldo.disponivel());

        saldo.reservar(3);
        assertEquals(5, saldo.getFisico());
        assertEquals(3, saldo.getReservado());
        assertEquals(2, saldo.disponivel());

        var semDisponivel = assertThrows(ConflitoException.class, () -> saldo.registrarSaidaAjuste(3));
        assertEquals("ESTOQUE_INSUFICIENTE", semDisponivel.code());

        saldo.registrarSaidaAjuste(2);
        assertEquals(3, saldo.getFisico());
        assertEquals(0, saldo.disponivel());

        var reservaExcesso = assertThrows(ConflitoException.class, () -> saldo.reservar(1));
        assertEquals("ESTOQUE_INSUFICIENTE", reservaExcesso.code());

        saldo.baixarReserva(2);
        assertEquals(1, saldo.getFisico());
        assertEquals(1, saldo.getReservado());

        var baixaExcesso = assertThrows(ConflitoException.class, () -> saldo.baixarReserva(2));
        assertEquals("RESERVA_INSUFICIENTE", baixaExcesso.code());

        saldo.liberar(1);
        assertEquals(1, saldo.getFisico());
        assertEquals(0, saldo.getReservado());

        assertThrows(ValidacaoException.class, () -> saldo.registrarEntrada(0));
    }
}

