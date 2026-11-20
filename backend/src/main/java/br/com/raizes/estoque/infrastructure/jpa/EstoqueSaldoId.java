package br.com.raizes.estoque.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class EstoqueSaldoId implements Serializable {
    @Column(name = "unidade_id", nullable = false)
    private Long unidadeId;

    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    protected EstoqueSaldoId() {
    }

    public EstoqueSaldoId(Long unidadeId, Long produtoId) {
        this.unidadeId = unidadeId;
        this.produtoId = produtoId;
    }

    public Long getUnidadeId() {
        return unidadeId;
    }

    public Long getProdutoId() {
        return produtoId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EstoqueSaldoId that)) return false;
        return Objects.equals(unidadeId, that.unidadeId) && Objects.equals(produtoId, that.produtoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unidadeId, produtoId);
    }
}
