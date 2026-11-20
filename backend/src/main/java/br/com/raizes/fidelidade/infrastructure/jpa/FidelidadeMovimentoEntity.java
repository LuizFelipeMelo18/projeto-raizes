package br.com.raizes.fidelidade.infrastructure.jpa;

import br.com.raizes.fidelidade.domain.TipoMovimentoFidelidade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "fidelidade_movimento")
public class FidelidadeMovimentoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;
    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;
    @Column(name = "ator_id")
    private Long atorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimentoFidelidade tipo;
    @Column(nullable = false)
    private int pontos;
    @Column(name = "saldo_apos", nullable = false)
    private int saldoApos;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected FidelidadeMovimentoEntity() {
    }

    public FidelidadeMovimentoEntity(Long usuarioId, Long pedidoId, Long atorId, TipoMovimentoFidelidade tipo,
                                     int pontos, int saldoApos) {
        this.usuarioId = usuarioId;
        this.pedidoId = pedidoId;
        this.atorId = atorId;
        this.tipo = tipo;
        this.pontos = pontos;
        this.saldoApos = saldoApos;
        this.criadoEm = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public Long getPedidoId() { return pedidoId; }
    public TipoMovimentoFidelidade getTipo() { return tipo; }
    public int getPontos() { return pontos; }
    public int getSaldoApos() { return saldoApos; }
    public Instant getCriadoEm() { return criadoEm; }
}

