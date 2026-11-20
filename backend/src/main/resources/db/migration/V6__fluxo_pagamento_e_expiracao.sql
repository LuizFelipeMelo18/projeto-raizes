ALTER TABLE pedido DROP CONSTRAINT ck_pedido_status;
ALTER TABLE pedido ADD CONSTRAINT ck_pedido_status CHECK (status IN (
    'AGUARDANDO_PAGAMENTO', 'PAGAMENTO_INDETERMINADO', 'EM_PREPARO', 'PRONTO', 'ENTREGUE',
    'PAGAMENTO_RECUSADO', 'PAGAMENTO_FALHOU', 'CANCELADO'
));
ALTER TABLE pedido ADD COLUMN reserva_expira_em TIMESTAMP WITH TIME ZONE;
UPDATE pedido SET reserva_expira_em = criado_em + INTERVAL '30 minutes'
    WHERE status = 'AGUARDANDO_PAGAMENTO';
CREATE INDEX ix_pedido_reserva_expiracao
    ON pedido (reserva_expira_em, id) WHERE status = 'AGUARDANDO_PAGAMENTO';

ALTER TABLE pagamento DROP CONSTRAINT ck_pagamento_status;
ALTER TABLE pagamento ADD CONSTRAINT ck_pagamento_status CHECK (status IN (
    'PENDENTE', 'INDETERMINADO', 'APROVADO', 'RECUSADO', 'FALHA_TECNICA'
));
ALTER TABLE pagamento DROP CONSTRAINT ck_pagamento_cenario;
ALTER TABLE pagamento ADD CONSTRAINT ck_pagamento_cenario CHECK (cenario_mock IN (
    'APROVAR', 'RECUSAR', 'FALHA_TECNICA', 'INDETERMINADO_APROVAR',
    'INDETERMINADO_RECUSAR', 'INDETERMINADO_PENDENTE'
));
ALTER TABLE pagamento ADD COLUMN payload_envio TEXT NOT NULL DEFAULT '{}';
ALTER TABLE pagamento ADD COLUMN payload_retorno TEXT;
ALTER TABLE pagamento ALTER COLUMN payload_envio DROP DEFAULT;
