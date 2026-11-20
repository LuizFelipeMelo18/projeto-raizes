package br.com.raizes.estoque.infrastructure.jpa;

import br.com.raizes.estoque.domain.TipoMovimentoEstoque;
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
@Table(name = "movimento_estoque")
public class MovimentoEstoqueEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "unidade_id", nullable = false)
    private Long unidadeId;
    @Column(name = "produto_id", nullable = false)
    private Long produtoId;
    @Column(name = "pedido_id")
    private Long pedidoId;
    @Column(name = "ator_id")
    private Long atorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimentoEstoque tipo;
    @Column(nullable = false)
    private int quantidade;
    @Column(length = 200)
    private String motivo;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected MovimentoEstoqueEntity() {
    }

    public MovimentoEstoqueEntity(Long unidadeId, Long produtoId, Long pedidoId, Long atorId,
                                  TipoMovimentoEstoque tipo, int quantidade, String motivo) {
        this.unidadeId = unidadeId;
        this.produtoId = produtoId;
        this.pedidoId = pedidoId;
        this.atorId = atorId;
        this.tipo = tipo;
        this.quantidade = quantidade;
        this.motivo = motivo;
        this.criadoEm = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUnidadeId() { return unidadeId; }
    public Long getProdutoId() { return produtoId; }
    public Long getPedidoId() { return pedidoId; }
    public Long getAtorId() { return atorId; }
    public TipoMovimentoEstoque getTipo() { return tipo; }
    public int getQuantidade() { return quantidade; }
    public String getMotivo() { return motivo; }
    public Instant getCriadoEm() { return criadoEm; }
}
