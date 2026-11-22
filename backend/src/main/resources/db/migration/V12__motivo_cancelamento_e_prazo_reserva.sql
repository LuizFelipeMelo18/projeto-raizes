ALTER TABLE pedido ADD COLUMN motivo_cancelamento VARCHAR(200);

UPDATE pedido
SET motivo_cancelamento = CASE
        WHEN status = 'PAGAMENTO_RECUSADO' THEN 'Pagamento simulado recusado.'
        ELSE 'Cancelamento anterior à atualização do histórico.'
    END,
    status = 'CANCELADO'
WHERE status IN ('PAGAMENTO_RECUSADO', 'CANCELADO');

UPDATE pedido
SET reserva_expira_em = criado_em + INTERVAL '15 minutes'
WHERE status = 'AGUARDANDO_PAGAMENTO';

ALTER TABLE pedido ADD CONSTRAINT ck_pedido_motivo_cancelamento CHECK (
    (status = 'CANCELADO' AND motivo_cancelamento IS NOT NULL)
    OR (status <> 'CANCELADO' AND motivo_cancelamento IS NULL)
);
