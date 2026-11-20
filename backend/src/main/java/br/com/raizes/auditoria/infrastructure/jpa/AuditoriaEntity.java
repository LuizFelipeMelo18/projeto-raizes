package br.com.raizes.auditoria.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auditoria")
public class AuditoriaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "unidade_id")
    private Long unidadeId;
    @Column(name = "pedido_id")
    private Long pedidoId;
    @Column(name = "ator_id")
    private Long atorId;
    @Column(name = "sujeito_usuario_id")
    private Long sujeitoUsuarioId;
    @Column(nullable = false, length = 60)
    private String acao;
    @Column(nullable = false, length = 40)
    private String resultado;
    @Column(length = 80)
    private String recurso;
    @Column(name = "request_id")
    private UUID requestId;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected AuditoriaEntity() {
    }

    public AuditoriaEntity(Long atorId, Long sujeitoUsuarioId, Long pedidoId, Long unidadeId,
                           String acao, String resultado, String recurso, UUID requestId) {
        this.atorId = atorId;
        this.sujeitoUsuarioId = sujeitoUsuarioId;
        this.pedidoId = pedidoId;
        this.unidadeId = unidadeId;
        this.acao = acao;
        this.resultado = resultado;
        this.recurso = recurso;
        this.requestId = requestId;
        this.criadoEm = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUnidadeId() { return unidadeId; }
    public Long getPedidoId() { return pedidoId; }
    public Long getAtorId() { return atorId; }
    public Long getSujeitoUsuarioId() { return sujeitoUsuarioId; }
    public String getAcao() { return acao; }
    public String getResultado() { return resultado; }
    public String getRecurso() { return recurso; }
    public UUID getRequestId() { return requestId; }
    public Instant getCriadoEm() { return criadoEm; }
}
