-- Auditoria com escopo por unidade para consulta autorizada de GERENTE/ADMIN.
ALTER TABLE auditoria
    ADD COLUMN unidade_id BIGINT REFERENCES unidade (id);

UPDATE auditoria a
SET unidade_id = p.unidade_id
FROM pedido p
WHERE a.pedido_id = p.id
  AND a.unidade_id IS NULL;

CREATE INDEX ix_auditoria_unidade_criado
    ON auditoria (unidade_id, criado_em DESC, id DESC);
CREATE INDEX ix_auditoria_acao_recurso_criado
    ON auditoria (acao, recurso, criado_em DESC, id DESC);

