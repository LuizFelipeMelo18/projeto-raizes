package br.com.raizes.identidade.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consentimento")
public class ConsentimentoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;
    @Column(nullable = false, length = 30)
    private String finalidade;
    @Column(nullable = false)
    private boolean consentido;
    @Column(name = "versao_texto", nullable = false, length = 30)
    private String versaoTexto;
    @Column(name = "request_id", nullable = false)
    private UUID requestId;
    @Column(name = "registrado_em", nullable = false)
    private Instant registradoEm;

    protected ConsentimentoEntity() {
    }

    public ConsentimentoEntity(Long usuarioId, boolean consentido, UUID requestId) {
        this.usuarioId = usuarioId;
        this.finalidade = "FIDELIDADE";
        this.consentido = consentido;
        this.versaoTexto = "v1";
        this.requestId = requestId;
        this.registradoEm = Instant.now();
    }

    public boolean isConsentido() { return consentido; }
    public String getVersaoTexto() { return versaoTexto; }
    public Instant getRegistradoEm() { return registradoEm; }
}
