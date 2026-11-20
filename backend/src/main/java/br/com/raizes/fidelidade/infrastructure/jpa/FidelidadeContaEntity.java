package br.com.raizes.fidelidade.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "fidelidade_conta")
public class FidelidadeContaEntity {
    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;
    @Column(nullable = false)
    private int saldo;
    @Version
    @Column(nullable = false)
    private long versao;
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected FidelidadeContaEntity() {
    }

    public void creditar(int pontos) {
        if (pontos <= 0) throw new IllegalArgumentException("Crédito deve ser positivo.");
        saldo = Math.addExact(saldo, pontos);
        atualizadoEm = Instant.now();
    }

    public void debitar(int pontos) {
        if (pontos <= 0 || pontos > saldo) throw new IllegalStateException("Débito inválido para o saldo.");
        saldo -= pontos;
        atualizadoEm = Instant.now();
    }

    public Long getUsuarioId() { return usuarioId; }
    public int getSaldo() { return saldo; }
}

