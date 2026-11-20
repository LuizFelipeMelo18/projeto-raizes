package br.com.raizes.pagamentos.infrastructure.jpa;

import br.com.raizes.pagamentos.domain.StatusPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pagamento")
public class PagamentoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "pedido_id", nullable = false, unique = true)
    private Long pedidoId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPagamento status;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;
    @Column(name = "chave_idempotencia", nullable = false, unique = true)
    private UUID chaveIdempotencia;
    @Column(name = "codigo_mock", length = 40)
    private String codigoMock;
    @Column(name = "cenario_mock", nullable = false, length = 32)
    private String cenarioMock;
    @Column(name = "payload_envio", nullable = false, columnDefinition = "text")
    private String payloadEnvio;
    @Column(name = "payload_retorno", columnDefinition = "text")
    private String payloadRetorno;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;
    @Column(name = "respondido_em")
    private Instant respondidoEm;

    protected PagamentoEntity() {
    }

    public PagamentoEntity(Long pedidoId, UUID chaveIdempotencia, String cenarioMock, BigDecimal valor,
                           String payloadEnvio) {
        this.pedidoId = pedidoId;
        this.chaveIdempotencia = chaveIdempotencia;
        this.cenarioMock = cenarioMock;
        this.valor = valor;
        this.payloadEnvio = payloadEnvio;
        this.status = StatusPagamento.PENDENTE;
        this.criadoEm = Instant.now();
    }

    public void registrarResposta(StatusPagamento status, String codigoMock, String payloadRetorno) {
        this.status = status;
        this.codigoMock = codigoMock;
        this.payloadRetorno = payloadRetorno;
        this.respondidoEm = Instant.now();
    }

    public Long getId() { return id; }
    public Long getPedidoId() { return pedidoId; }
    public UUID getChaveIdempotencia() { return chaveIdempotencia; }
    public String getCenarioMock() { return cenarioMock; }
    public String getCodigoMock() { return codigoMock; }
    public String getPayloadEnvio() { return payloadEnvio; }
    public String getPayloadRetorno() { return payloadRetorno; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getRespondidoEm() { return respondidoEm; }
    public StatusPagamento getStatus() { return status; }
    public BigDecimal getValor() { return valor; }
}
