package br.com.raizes.estoque.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import br.com.raizes.shared.domain.ConflitoException;
import br.com.raizes.shared.domain.ValidacaoException;

@Entity
@Table(name = "estoque_saldo")
public class EstoqueSaldoEntity {
    @EmbeddedId
    private EstoqueSaldoId id;

    @Column(nullable = false)
    private int fisico;

    @Column(nullable = false)
    private int reservado;

    @Version
    @Column(nullable = false)
    private long versao;

    protected EstoqueSaldoEntity() {
    }

    public EstoqueSaldoId getId() { return id; }
    public int getFisico() { return fisico; }
    public int getReservado() { return reservado; }

    public int disponivel() {
        return fisico - reservado;
    }

    public void registrarEntrada(int quantidade) {
        positiva(quantidade);
        try {
            fisico = Math.addExact(fisico, quantidade);
        } catch (ArithmeticException ex) {
            throw new ConflitoException("LIMITE_ESTOQUE", "Saldo físico excede o limite suportado.");
        }
    }

    public void registrarSaidaAjuste(int quantidade) {
        positiva(quantidade);
        if (quantidade > disponivel()) {
            throw new ConflitoException("ESTOQUE_INSUFICIENTE", "Quantidade disponível insuficiente na unidade.");
        }
        fisico -= quantidade;
    }

    public void reservar(int quantidade) {
        positiva(quantidade);
        if (quantidade > disponivel()) {
            throw new ConflitoException("ESTOQUE_INSUFICIENTE", "Quantidade disponível insuficiente na unidade.");
        }
        reservado += quantidade;
    }

    public void liberar(int quantidade) {
        positiva(quantidade);
        if (quantidade > reservado) {
            throw new ConflitoException("RESERVA_INSUFICIENTE", "Reserva insuficiente para liberar.");
        }
        reservado -= quantidade;
    }

    public void baixarReserva(int quantidade) {
        positiva(quantidade);
        if (quantidade > reservado) {
            throw new ConflitoException("RESERVA_INSUFICIENTE", "Reserva insuficiente para baixar.");
        }
        reservado -= quantidade;
        fisico -= quantidade;
    }

    private static void positiva(int quantidade) {
        if (quantidade <= 0) {
            throw new ValidacaoException("QUANTIDADE_INVALIDA", "Quantidade deve ser positiva.");
        }
    }
}
