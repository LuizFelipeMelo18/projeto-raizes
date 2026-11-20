ALTER TABLE pedido
    ADD COLUMN chave_idempotencia UUID,
    ADD COLUMN hash_requisicao CHAR(64),
    ADD COLUMN codigo_acompanhamento VARCHAR(32);

ALTER TABLE pedido ADD CONSTRAINT uq_pedido_chave_idempotencia UNIQUE (chave_idempotencia);
ALTER TABLE pedido ADD CONSTRAINT uq_pedido_codigo_acompanhamento UNIQUE (codigo_acompanhamento);
ALTER TABLE pedido ADD CONSTRAINT ck_pedido_idempotencia_par
    CHECK ((chave_idempotencia IS NULL) = (hash_requisicao IS NULL));
ALTER TABLE pedido ADD CONSTRAINT ck_pedido_anonimo_acompanhavel
    CHECK (cliente_id IS NOT NULL OR codigo_acompanhamento IS NOT NULL);

CREATE INDEX ix_pedido_cliente_criacao ON pedido (cliente_id, criado_em DESC, id DESC);
CREATE INDEX ix_pedido_unidade_canal_status_criacao
    ON pedido (unidade_id, canal_pedido, status, criado_em DESC, id DESC);
