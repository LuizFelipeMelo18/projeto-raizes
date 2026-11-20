package br.com.raizes.pagamentos.infrastructure.jpa;

import br.com.raizes.pagamentos.domain.StatusPagamento;
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
@Table(name = "pagamento_evento")
public class PagamentoEventoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "pagamento_id", nullable = false)
    private Long pagamentoId;
    @Column(nullable = false, length = 20)
    private String operacao;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPagamento status;
    @Column(name = "codigo_mock", length = 40)
    private String codigoMock;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(name = "registrado_em", nullable = false)
    private Instant registradoEm;

    protected PagamentoEventoEntity() { }

    public PagamentoEventoEntity(Long pagamentoId, String operacao, StatusPagamento status,
                                  String codigoMock, String payload) {
        this.pagamentoId = pagamentoId;
        this.operacao = operacao;
        this.status = status;
        this.codigoMock = codigoMock;
        this.payload = payload;
        this.registradoEm = Instant.now();
    }
}
