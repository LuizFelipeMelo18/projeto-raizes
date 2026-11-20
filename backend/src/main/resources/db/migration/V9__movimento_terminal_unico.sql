CREATE UNIQUE INDEX uq_movimento_pedido_produto_terminal
    ON movimento_estoque (pedido_id, produto_id, tipo)
    WHERE tipo IN ('BAIXA', 'LIBERACAO');
