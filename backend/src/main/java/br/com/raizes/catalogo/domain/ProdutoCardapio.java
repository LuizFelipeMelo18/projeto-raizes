package br.com.raizes.catalogo.domain;

import java.math.BigDecimal;

public record ProdutoCardapio(Long produtoId, String nome, BigDecimal preco, boolean disponivel) {
}
