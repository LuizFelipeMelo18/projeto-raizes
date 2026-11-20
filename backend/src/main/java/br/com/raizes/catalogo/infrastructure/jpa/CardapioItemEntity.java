package br.com.raizes.catalogo.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "cardapio_item")
public class CardapioItemEntity {
    @EmbeddedId
    private CardapioItemId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", insertable = false, updatable = false)
    private ProdutoEntity produto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    private boolean ativo;

    protected CardapioItemEntity() {
    }

    public CardapioItemId getId() {
        return id;
    }

    public ProdutoEntity getProduto() {
        return produto;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
