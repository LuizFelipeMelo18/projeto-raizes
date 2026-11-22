package br.com.raizes.pedidos.infrastructure.jpa;

import br.com.raizes.pedidos.domain.CanalPedido;
import br.com.raizes.pedidos.domain.ModalidadeAtendimento;
import br.com.raizes.pedidos.domain.PedidoEscopo;
import br.com.raizes.pedidos.domain.StatusPedido;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Version;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pedido")
public class PedidoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "unidade_id", nullable = false)
    private Long unidadeId;
    @Column(name = "cliente_id")
    private Long clienteId;
    @Column(name = "criado_por_id", nullable = false)
    private Long criadoPorId;
    @Enumerated(EnumType.STRING)
    @Column(name = "canal_pedido", nullable = false)
    private CanalPedido canalPedido;
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidade_atendimento", nullable = false)
    private ModalidadeAtendimento modalidadeAtendimento;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPedido status;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;
    @Column(name = "desconto_fidelidade", nullable = false, precision = 12, scale = 2)
    private BigDecimal descontoFidelidade;
    @Column(name = "pontos_resgatados", nullable = false)
    private int pontosResgatados;
    @Column(name = "participa_fidelidade", nullable = false)
    private boolean participaFidelidade;
    @Column(name = "forma_pagamento", nullable = false)
    private String formaPagamento;
    @Version
    @Column(nullable = false)
    private long versao;
    @Column(name = "chave_idempotencia", unique = true)
    private UUID chaveIdempotencia;
    @Column(name = "hash_requisicao", length = 64)
    private String hashRequisicao;
    @Column(name = "codigo_acompanhamento", length = 32, unique = true)
    private String codigoAcompanhamento;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
    @Column(name = "reserva_expira_em")
    private Instant reservaExpiraEm;
    @Column(name = "motivo_cancelamento", length = 200)
    private String motivoCancelamento;

    protected PedidoEntity() {
    }

    public PedidoEntity(Long unidadeId, Long clienteId, Long criadoPorId, CanalPedido canalPedido,
                        ModalidadeAtendimento modalidadeAtendimento, BigDecimal total,
                        BigDecimal descontoFidelidade, int pontosResgatados, boolean participaFidelidade,
                        UUID chaveIdempotencia, String hashRequisicao, String codigoAcompanhamento) {
        this.unidadeId = unidadeId;
        this.clienteId = clienteId;
        this.criadoPorId = criadoPorId;
        this.canalPedido = canalPedido;
        this.modalidadeAtendimento = modalidadeAtendimento;
        this.status = StatusPedido.AGUARDANDO_PAGAMENTO;
        this.total = total;
        this.descontoFidelidade = descontoFidelidade;
        this.pontosResgatados = pontosResgatados;
        this.participaFidelidade = participaFidelidade;
        this.formaPagamento = "MOCK";
        this.chaveIdempotencia = chaveIdempotencia;
        this.hashRequisicao = hashRequisicao;
        this.codigoAcompanhamento = codigoAcompanhamento;
        this.criadoEm = Instant.now();
        this.atualizadoEm = this.criadoEm;
        this.reservaExpiraEm = this.criadoEm.plus(java.time.Duration.ofMinutes(15));
    }

    public Long getId() { return id; }
    public Long getUnidadeId() { return unidadeId; }
    public Long getClienteId() { return clienteId; }
    public Long getCriadoPorId() { return criadoPorId; }
    public String getHashRequisicao() { return hashRequisicao; }
    public String getCodigoAcompanhamento() { return codigoAcompanhamento; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public Instant getReservaExpiraEm() { return reservaExpiraEm; }
    public String getMotivoCancelamento() { return motivoCancelamento; }
    public CanalPedido getCanalPedido() { return canalPedido; }
    public ModalidadeAtendimento getModalidadeAtendimento() { return modalidadeAtendimento; }
    public StatusPedido getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getDescontoFidelidade() { return descontoFidelidade; }
    public int getPontosResgatados() { return pontosResgatados; }
    public boolean isParticipaFidelidade() { return participaFidelidade; }
    public BigDecimal getSubtotalItens() { return total.add(descontoFidelidade); }
    public Instant getCriadoEm() { return criadoEm; }
    public void mudarStatus(StatusPedido novoStatus) {
        if (novoStatus == StatusPedido.CANCELADO) {
            throw new IllegalArgumentException("Cancelamento exige motivo.");
        }
        this.status = novoStatus;
        this.atualizadoEm = Instant.now();
    }
    public void cancelar(String motivo) {
        if (motivo == null || motivo.isBlank() || motivo.length() > 200) {
            throw new IllegalArgumentException("Motivo de cancelamento inválido.");
        }
        this.status = StatusPedido.CANCELADO;
        this.motivoCancelamento = motivo;
        this.atualizadoEm = Instant.now();
    }
    public PedidoEscopo escopo() { return new PedidoEscopo(unidadeId, clienteId, criadoPorId); }
}
