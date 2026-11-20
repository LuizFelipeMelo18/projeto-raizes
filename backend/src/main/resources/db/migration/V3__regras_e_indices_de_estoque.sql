ALTER TABLE movimento_estoque ADD CONSTRAINT ck_movimento_origem CHECK (
    (tipo IN ('ENTRADA', 'SAIDA_AJUSTE') AND pedido_id IS NULL)
    OR (tipo IN ('RESERVA', 'LIBERACAO', 'BAIXA') AND pedido_id IS NOT NULL)
);

CREATE INDEX ix_movimento_unidade_criado_id
    ON movimento_estoque (unidade_id, criado_em DESC, id DESC);
CREATE INDEX ix_movimento_unidade_produto_criado_id
    ON movimento_estoque (unidade_id, produto_id, criado_em DESC, id DESC);
CREATE INDEX ix_produto_ativo_id ON produto (ativo, id);
