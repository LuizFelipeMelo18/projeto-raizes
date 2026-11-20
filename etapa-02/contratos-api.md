# Etapa 02 — contratos propostos da API do Fluxo A

Base URL local: `/api/v1`. Contratos iniciados na tarefa 02 e revistos até a tarefa 07; OpenAPI executável em `backend/src/main/resources/static/openapi.yaml`. Identificadores são `bigint`, valores monetários são BRL com duas casas. Datas são ISO 8601 UTC. Os exemplos usam dados fictícios.

**Revisão da etapa 07:** os contratos de pagamento e estados abaixo refletem a API implementada. Resultado técnico indeterminado retorna 202 e retém a reserva até reconciliação; substitui a liberação imediata prevista na primeira modelagem.

**Revisão da etapa 08:** fidelidade e consentimento estão implementados (`/api/v1/fidelidade/consentimento`, `/api/v1/fidelidade/saldo`, `/api/v1/fidelidade/movimentacoes` e `/api/v1/usuarios/me/consentimentos/fidelidade`). `POST /api/v1/pedidos` aceita `pontosResgate` opcional (múltiplo de 100) e retorna `subtotalItens`, `descontoFidelidade`, `pontosResgatados`, `participaFidelidade` e `total` (valor final a pagar). Taxa de acúmulo: **1 ponto por R\$ 1,00 inteiro pago** quando o pedido atinge `ENTREGUE` com consentimento vigente. Taxa de resgate: **100 pontos = R\$ 5,00** de desconto, limitado a **50% do subtotal dos itens**. Recusa, cancelamento ou expiração estornam automaticamente os pontos resgatados.

**Revisão da etapa 09:** foram adicionados os contratos executáveis de consulta autorizada e paginada de auditoria (`GET /api/v1/auditoria`) e de relatório consolidado de pedidos por unidade e canal (`GET /api/v1/pedidos/relatorios/unidades-canais`), restritos a `GERENTE` (escopo da própria unidade) e `ADMIN` (escopo global).

Exemplos de operações diferentes ilustram ramos independentes: repetir o mesmo `pedidoId` em exemplos de aprovação e cancelamento não indica que ambas ocorreriam no mesmo pedido.

## Convenções comuns

- JWT `Authorization: Bearer <token>` em rotas protegidas. Papéis: `CLIENTE`, `TOTEM`, `ATENDENTE`, `COZINHA`, `GERENTE`. Cada principal de equipe fica limitado à própria unidade no MVP; Cliente vê apenas seus pedidos.
- Compatibilidade na criação: `CLIENTE` → `APP`, `WEB` ou `PICKUP`; `TOTEM` → `TOTEM`; `ATENDENTE` → `BALCAO` ou `PICKUP`. `clienteId` não é aceito no request: é o ID do token de Cliente ou `null` nas vendas de Totem/Atendente sem cliente cadastrado.
- `canalPedido` é obrigatório e registra origem. `modalidadeAtendimento` é outro campo, obrigatório, com `RETIRADA` ou `CONSUMO_LOCAL`. Nenhuma modalidade é inferida do canal. `formaPagamento=MOCK` indica somente a integração simulada.
- Listagens usam `page` a partir de 1, `limit` padrão 20 (máximo 100), ordenação estável por `criadoEm DESC, id DESC` quando aplicável, e envelope `{data,page,limit,totalItems,totalPages}`.
- JSON malformado ou parâmetro sintaticamente ilegível → 400; campo ausente, enum/valor inválido → 422; sem autenticação → 401; papel/escopo/posse inválido → 403 (ou 404 quando revelar existência do pedido seria indevido); recurso inexistente → 404; falta de estoque, estado ou idempotência conflitante → 409; resultado técnico indeterminado do mock → 202.
- Todos os erros seguem o mesmo esquema. `details` pode ser vazio; não contém senha, token ou dados financeiros. O `requestId` também é registrado na auditoria.

```json
{
  "error": "ESTOQUE_INSUFICIENTE",
  "message": "Quantidade indisponível na unidade informada.",
  "details": [{"field": "itens[0].quantidade", "issue": "Disponível: 1"}],
  "timestamp": "2026-11-20T16:00:00Z",
  "path": "/api/v1/pedidos",
  "requestId": "9b5f7406-39e2-4931-ae6f-6142edfa9fa6"
}
```

**Catálogo de rotas:**

| Recurso | Método/rota | Finalidade | Papel |
|---|---|---|---|
| Auth | `POST /auth/login` | Obter token | Público |
| Unidades | `GET /unidades` | Listar unidades ativas | Público |
| Cardápio | `GET /unidades/{id}/cardapio` | Consultar produtos/preços de uma unidade | Público |
| Pedidos | `POST /pedidos` | Criar pedido e reservar estoque | CLIENTE/TOTEM/ATENDENTE |
| Pedidos | `GET /pedidos` | Listar e filtrar por canal/status | CLIENTE/ATENDENTE/COZINHA/GERENTE |
| Pedidos | `GET /pedidos/{id}` | Consultar pedido | CLIENTE/ATENDENTE/COZINHA/GERENTE |
| Pagamentos | `POST /pedidos/{id}/pagamentos` | Solicitar mock e registrar resultado | CLIENTE/TOTEM/ATENDENTE |
| Pedidos | `PATCH /pedidos/{id}/status` | Marcar pronto ou entregue | COZINHA/ATENDENTE/GERENTE |
| Pedidos | `POST /pedidos/{id}/cancelamento` | Cancelar antes de pagar | CLIENTE/TOTEM/ATENDENTE |
| Estoque | `GET /estoque` | Consultar saldo por unidade | GERENTE/ADMIN |
| Estoque | `POST /estoque/movimentacoes` | Entrada/saída manual | GERENTE/ADMIN |
| Fidelidade | `GET /fidelidade/consentimento` | Consultar estado vigente do consentimento | CLIENTE |
| Fidelidade | `PUT /fidelidade/consentimento` | Conceder ou revogar consentimento (`PUT /usuarios/me/consentimentos/fidelidade` equivalente) | CLIENTE |
| Fidelidade | `GET /fidelidade/saldo` | Consultar saldo, totais por tipo, conferência do ledger e regras vigentes | CLIENTE |
| Fidelidade | `GET /fidelidade/movimentacoes` | Listar histórico paginado de acúmulos, resgates e estornos do próprio cliente | CLIENTE |
| Auditoria | `GET /auditoria` | Consultar eventos de auditoria paginados por unidade/ação/recurso/pedido/ator | GERENTE/ADMIN |
| Relatórios | `GET /pedidos/relatorios/unidades-canais` | Consolidar pedidos, subtotais, descontos e totais por unidade e canal | GERENTE/ADMIN |

Os caminhos do catálogo são relativos a `/api/v1`. Promoções/campanhas e CRUD administrativo de cardápio/unidades permanecem conceituais na matriz da etapa 01. Usuários, unidades e produtos iniciais são disponibilizados por seed local (`R__demo_seed.sql`).

## 1. Login

**Finalidade:** autenticar usuário/papel. **Método/rota:** `POST /api/v1/auth/login`. **Permissão:** público. **Parâmetros:** nenhum path/query; `Content-Type: application/json`.

Request:

```json
{"email":"cliente@exemplo.com","senha":"Senha@123"}
```

Response 200:

```json
{"accessToken":"jwt-de-exemplo","tokenType":"Bearer","expiresIn":3600,"user":{"id":123,"nome":"Ana","perfil":"CLIENTE"}}
```

**Códigos:** 200; 400 JSON inválido; 401 credenciais inválidas (`CREDENCIAIS_INVALIDAS`); 422 email/senha ausentes ou formato inválido. **Erro:** esquema comum, sem eco da senha.

## 2. Listar unidades

**Finalidade:** selecionar unidade ativa. **Método/rota:** `GET /api/v1/unidades`. **Permissão:** público. **Parâmetros:** query opcionais `page`, `limit`; sem path/body.

Request: `GET /api/v1/unidades?page=1&limit=20`

Response 200:

```json
{"data":[{"id":2,"nome":"Unidade Centro","ativa":true}],"page":1,"limit":20,"totalItems":1,"totalPages":1}
```

**Códigos:** 200; 400 query ilegível; 422 paginação fora dos limites. **Erro:** esquema comum.

## 3. Consultar cardápio da unidade

**Finalidade:** obter produtos, preço vigente e indicação de disponibilidade. **Método/rota:** `GET /api/v1/unidades/{id}/cardapio`. **Permissão:** público. **Parâmetros:** path `id` = unidade; query `page`, `limit`; sem body.

Request: `GET /api/v1/unidades/2/cardapio?page=1&limit=20`

Response 200:

```json
{"data":[{"produtoId":10,"nome":"Sanduíche regional","preco":39.90,"disponivel":true}],"page":1,"limit":20,"totalItems":1,"totalPages":1}
```

`disponivel` é indicativo; a reserva na criação decide de forma transacional. **Códigos:** 200; 400 path/query ilegível; 404 unidade inexistente/inativa; 422 paginação inválida. **Erro:** esquema comum.

## 4. Criar pedido

**Finalidade:** capturar itens/preços, aplicar eventual resgate de fidelidade, reservar estoque e abrir pagamento. **Método/rota:** `POST /api/v1/pedidos`. **Permissão:** JWT `CLIENTE`, `TOTEM` ou `ATENDENTE`, conforme canal; principal de equipe restrito à unidade. **Parâmetros:** header obrigatório `Idempotency-Key` (UUID); body obrigatório; `pontosResgate` opcional (múltiplo de 100, padrão 0). `clienteId`, preço, subtotal, desconto e total são rejeitados se enviados.

Request:

```json
{
  "unidadeId": 2,
  "canalPedido": "APP",
  "modalidadeAtendimento": "RETIRADA",
  "itens": [{"produtoId": 10, "quantidade": 2}],
  "formaPagamento": "MOCK",
  "pontosResgate": 100
}
```

Response 201 (`Location: /api/v1/pedidos/9001`):

```json
{
  "pedidoId": 9001,
  "unidadeId": 2,
  "canalPedido": "APP",
  "modalidadeAtendimento": "RETIRADA",
  "status": "AGUARDANDO_PAGAMENTO",
  "subtotalItens": 79.80,
  "descontoFidelidade": 5.00,
  "pontosResgatados": 100,
  "participaFidelidade": true,
  "total": 74.80,
  "itens": [{"produtoId": 10, "quantidade": 2, "precoUnitario": 39.90, "subtotal": 79.80}],
  "criadoEm": "2026-11-20T16:00:00Z",
  "codigoAcompanhamento": null
}
```

**Códigos:** 201 (ou 200 em replay idempotente); 400 JSON inválido; 401 sem token; 403 papel, canal ou unidade incompatível; 404 unidade/produto ausente; 409 estoque insuficiente, item inativo na unidade, pedido anônimo/sem consentimento tentando resgatar (`FIDELIDADE_EXIGE_CLIENTE`, `CONSENTIMENTO_FIDELIDADE_INATIVO`), desconto acima de 50% do subtotal (`LIMITE_RESGATE_EXCEDIDO`) ou saldo de pontos insuficiente (`SALDO_PONTOS_INSUFICIENTE`); 422 campo obrigatório, enum, quantidade, `pontosResgate` fora do múltiplo de 100 (`PONTOS_RESGATE_INVALIDOS`) ou itens inválidos. A reserva de estoque, o débito `RESGATE` e o pedido são atômicos.

## 5. Listar/filtrar pedidos

**Finalidade:** acompanhar pedidos por canal e estado. **Método/rota:** `GET /api/v1/pedidos`. **Permissão:** JWT `CLIENTE` (somente próprios) ou `ATENDENTE`/`COZINHA`/`GERENTE` (própria unidade). **Parâmetros:** query `canalPedido` e `status` opcionais; `page`, `limit`; `unidadeId` opcional para equipe, mas deve corresponder à unidade do token. Sem path/body.

Request: `GET /api/v1/pedidos?canalPedido=APP&status=AGUARDANDO_PAGAMENTO&page=1&limit=20`

Response 200:

```json
{"data":[{"pedidoId":9001,"unidadeId":2,"canalPedido":"APP","modalidadeAtendimento":"RETIRADA","status":"AGUARDANDO_PAGAMENTO","total":79.80,"criadoEm":"2026-11-20T16:00:00Z"}],"page":1,"limit":20,"totalItems":1,"totalPages":1}
```

**Códigos:** 200; 400 query ilegível; 401 sem token; 403 papel/escopo indevido; 422 enum ou paginação inválida. **Erro:** esquema comum. O filtro é aplicado depois do escopo de acesso; lista vazia retorna 200 com `data: []`.

## 6. Consultar pedido

**Finalidade:** consultar dados e estado do pedido. **Método/rota:** `GET /api/v1/pedidos/{id}`. **Permissão:** JWT `CLIENTE` dono ou `ATENDENTE`/`COZINHA`/`GERENTE` da unidade. Totem não consulta pedidos antigos por esta rota compartilhada. **Parâmetros:** path `id`; sem query/body.

Request: `GET /api/v1/pedidos/9001`

Response 200:

```json
{"pedidoId":9001,"unidadeId":2,"canalPedido":"APP","modalidadeAtendimento":"RETIRADA","status":"EM_PREPARO","total":79.80,"itens":[{"produtoId":10,"quantidade":2,"precoUnitario":39.90,"subtotal":79.80}],"pagamento":{"status":"APROVADO","valor":79.80},"criadoEm":"2026-11-20T16:00:00Z"}
```

**Códigos:** 200; 400 ID ilegível; 401 sem token; 403 equipe fora do escopo; 404 inexistente ou não pertencente ao Cliente. **Erro:** esquema comum. O response não contém senha, token ou dados financeiros.

## 7. Solicitar pagamento mock

**Finalidade:** registrar uma tentativa e refletir o resultado no pedido. **Método/rota:** `POST /api/v1/pedidos/{id}/pagamentos`. **Permissão:** JWT `CLIENTE` dono, `TOTEM` que criou o pedido ou `ATENDENTE` da unidade. **Parâmetros:** path `id`; header obrigatório `Idempotency-Key` (UUID); body `cenarioMock` = `APROVAR`, `RECUSAR`, `FALHA_TECNICA`, `INDETERMINADO_APROVAR`, `INDETERMINADO_RECUSAR` ou `INDETERMINADO_PENDENTE`. Os três últimos permitem demonstrar consulta posterior; nenhum cenário representa cartão/PIX real. A tentativa `PENDENTE` é persistida antes da chamada ao gateway, realizada sem transação de banco aberta.

Request:

```http
POST /api/v1/pedidos/9001/pagamentos
Idempotency-Key: 72ea2641-6557-4288-a93c-32ec0252a41a
Content-Type: application/json
```

```json
{"cenarioMock":"APROVAR"}
```

Response 200 aprovado:

```json
{"pagamentoId":7001,"pedidoId":9001,"statusPagamento":"APROVADO","statusPedido":"EM_PREPARO","valor":79.80,"mensagem":"Pagamento simulado aprovado.","codigoMock":"MOCK-OK"}
```

Response 200 recusado:

```json
{"pagamentoId":7002,"pedidoId":9002,"statusPagamento":"RECUSADO","statusPedido":"PAGAMENTO_RECUSADO","valor":79.80,"mensagem":"Pagamento simulado recusado.","codigoMock":"MOCK-DECLINED"}
```

Resposta indeterminada (incluindo `FALHA_TECNICA`) retorna **202**, Pagamento `INDETERMINADO` e Pedido `PAGAMENTO_INDETERMINADO`, mantendo a reserva. O payload de envio e a última resposta são gravados em `pagamento`; cada resposta é preservada em `pagamento_evento`. **Códigos:** 200, 202; 400 JSON/header ilegível; 401; 403; 404; 409 estado incompatível, segunda tentativa ou chave reutilizada com payload diferente; 422 chave ausente/cenário inválido. **Idempotência:** repetir mesma chave e mesmo body no mesmo pedido devolve o estado persistido sem novo movimento; pedido com outra chave após tentativa recebe 409.

### Reconciliar resultado incerto

**Finalidade:** consultar o gateway mock e concluir uma tentativa pendente/indeterminada. **Método/rota:** `POST /api/v1/pedidos/{id}/pagamentos/reconciliacao`. **Permissão:** `GERENTE` da unidade ou `ADMIN`. **Parâmetros:** path `id`; sem body/header especial. **Resposta:** mesmo JSON de pagamento; 200 se aprovado/recusado, 202 se ainda indeterminado. Aprovação baixa a reserva uma vez; recusa libera uma vez. Repetição após resolução retorna o resultado persistido. **Erros:** 400, 401, 403, 404, 409 no esquema comum. Resposta ainda incerta nunca autoriza liberação automática.

## 8. Atualizar status operacional

**Finalidade:** registrar avanço da produção/entrega. **Método/rota:** `PATCH /api/v1/pedidos/{id}/status`. **Permissão:** JWT `COZINHA` para `EM_PREPARO` → `PRONTO`; `ATENDENTE` para `PRONTO` → `ENTREGUE`; `GERENTE` da unidade ou `ADMIN` podem executar ambas com auditoria. **Parâmetros:** path `id`; body `status`. `EM_PREPARO` é exclusivo da aprovação do mock.

Request:

```json
{"status":"PRONTO"}
```

Response 200:

```json
{"pedidoId":9001,"statusAnterior":"EM_PREPARO","status":"PRONTO","reservaLiberada":false,"atualizadoEm":"2026-11-20T16:12:00Z"}
```

**Códigos:** 200; 400 ID/JSON ilegível; 401; 403 papel/escopo; 404; 409 transição inválida; 422 status ausente/inválido. **Erro:** esquema comum; transição inválida usa `TRANSICAO_INVALIDA`. Gerar registro de auditoria.

## 9. Cancelar pedido pendente

**Finalidade:** cancelar antes da tentativa de pagamento e liberar reserva. **Método/rota:** `POST /api/v1/pedidos/{id}/cancelamento`. **Permissão:** JWT `CLIENTE` dono, `TOTEM` criador ou `ATENDENTE` da unidade. **Parâmetros:** path `id`; body opcional com motivo curto; sem query.

Request:

```json
{"motivo":"Desistência antes do pagamento"}
```

Response 200:

```json
{"pedidoId":9001,"statusAnterior":"AGUARDANDO_PAGAMENTO","status":"CANCELADO","reservaLiberada":true,"atualizadoEm":"2026-11-20T16:03:00Z"}
```

**Códigos:** 200; 400 ID/JSON ilegível; 401; 403; 404; 409 tentativa já iniciada ou estado incompatível; 422 motivo acima do limite. **Erro:** esquema comum. A checagem e liberação são atômicas; o motivo não deve conter dados pessoais desnecessários.

Reservas de pedidos `AGUARDANDO_PAGAMENTO` sem tentativa vencem em 30 minutos e são canceladas/liberadas por tarefa agendada, com bloqueio do pedido e conferência da ausência de pagamento. Reservas com resultado indeterminado permanecem retidas até reconciliação.

## 10. Consultar estoque

**Finalidade:** conferir saldo por unidade/produto. **Método/rota:** `GET /api/v1/estoque`. **Permissão:** JWT `GERENTE` com escopo da unidade. **Parâmetros:** query `unidadeId` obrigatório, `produtoId` opcional, `page`, `limit`; sem path/body.

Request: `GET /api/v1/estoque?unidadeId=2&produtoId=10&page=1&limit=20`

Response 200:

```json
{"data":[{"unidadeId":2,"produtoId":10,"fisico":12,"reservado":2,"disponivel":10}],"page":1,"limit":20,"totalItems":1,"totalPages":1}
```

**Códigos:** 200; 400 query ilegível; 401; 403 escopo; 404 unidade/produto inexistente; 422 campo obrigatório/paginação inválidos. **Erro:** esquema comum.

## 11. Registrar movimentação manual de estoque

**Finalidade:** entrada ou saída de ajuste que sustenta a disponibilidade do fluxo A. **Método/rota:** `POST /api/v1/estoque/movimentacoes`. **Permissão:** JWT `GERENTE` com escopo da unidade. **Parâmetros:** sem path/query; body `unidadeId`, `produtoId`, `tipo` (`ENTRADA` ou `SAIDA_AJUSTE`), `quantidade` positiva e `motivo` curto. Saída manual só consome parte disponível, nunca reserva de pedido.

Request:

```json
{"unidadeId":2,"produtoId":10,"tipo":"ENTRADA","quantidade":10,"motivo":"Reposição local"}
```

Response 201:

```json
{"movimentoId":8001,"unidadeId":2,"produtoId":10,"tipo":"ENTRADA","quantidade":10,"saldo":{"fisico":22,"reservado":2,"disponivel":20},"criadoEm":"2026-11-20T16:05:00Z"}
```

**Códigos:** 201; 400 JSON inválido; 401; 403; 404 unidade/produto fora do cardápio; 409 saída acima do disponível; 422 tipo/quantidade/motivo inválidos. **Erro:** esquema comum. Persiste movimento e evento de auditoria.

## 12. Consentimento, saldo e histórico de fidelidade

**Regras do programa:**
- **Elegibilidade e LGPD:** participação exclusiva para `CLIENTE` autenticado com consentimento ativo para a finalidade `FIDELIDADE`. Pedidos anônimos (`TOTEM`/`BALCAO` sem cliente) ou criados quando o consentimento estava inativo não participam (`participaFidelidade=false`) e não geram pontos retroativos. Se o cliente revogar o consentimento antes da entrega, a transição para `ENTREGUE` não credita pontos.
- **Taxa de acúmulo:** `1 ponto` por cada `R$ 1,00` inteiro efetivamente pago (`floor(total)`), creditado exclusivamente quando o pedido atinge `ENTREGUE`. A constraint `UNIQUE(pedido_id, tipo)` em `fidelidade_movimento` e o bloqueio pessimista da conta impedem crédito duplicado em reprocessamento.
- **Taxa e limite de resgate:** resgate em blocos de `100 pontos = R$ 5,00` de desconto (`R$ 0,05/ponto`) em pedido posterior, limitado a **50% do subtotal dos itens** (`descontoFidelidade <= subtotalItens * 0.50`, logo `total > 0`).
- **Devolução automática:** quando um pedido com `pontosResgatados > 0` vai para `PAGAMENTO_RECUSADO` (direto ou na reconciliação) ou `CANCELADO` (manual antes do pagamento ou expiração de reserva), um movimento `ESTORNO_RESGATE` devolve os pontos uma única vez.

### 12.1 Consultar ou atualizar consentimento

- `GET /api/v1/fidelidade/consentimento` — JWT `CLIENTE` → 200 `{"consentido":true,"versaoTexto":"v1","registradoEm":"2026-11-20T18:00:00Z"}`.
- `PUT /api/v1/fidelidade/consentimento` (e `PUT /api/v1/usuarios/me/consentimentos/fidelidade`) — JWT `CLIENTE`, body `{"consentido":true}` ou `{"consentido":false}` → 200 com o novo evento persistido e auditado (`CONCEDIDO`/`REVOGADO`). **Erros:** 400, 401, 403 (perfis não-CLIENTE), 422.

### 12.2 Consultar saldo e conferência do ledger

- `GET /api/v1/fidelidade/saldo` — JWT `CLIENTE` (cada cliente consulta exclusivamente a própria conta).

Response 200:

```json
{
  "clienteId": 100,
  "consentimentoAtivo": true,
  "saldo": 53,
  "totalAcumulado": 153,
  "totalResgatado": 100,
  "totalEstornado": 0,
  "saldoCalculadoMovimentos": 53,
  "saldoConsistente": true,
  "regras": {
    "pontosPorRealEntregue": 1,
    "blocoResgatePontos": 100,
    "descontoPorBloco": 5.00,
    "limitePercentualSubtotal": 0.50
  }
}
```

### 12.3 Consultar histórico de movimentações

- `GET /api/v1/fidelidade/movimentacoes?page=1&limit=20` — JWT `CLIENTE`.

Response 200:

```json
{
  "data": [
    {"movimentoId": 1, "pedidoId": 9001, "tipo": "ACUMULO", "pontos": 119, "variacaoSaldo": 119, "saldoApos": 119, "criadoEm": "2026-11-20T18:10:00Z"}
  ],
  "page": 1,
  "limit": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

**Códigos:** 200; 400 query ilegível; 401 sem token; 403 perfil diferente de `CLIENTE`; 422 paginação inválida.

## 13. Consultar eventos de auditoria

**Finalidade:** consultar trilha de auditoria de criação/cancelamento/transição de pedido, pagamento mock, ajuste manual de estoque, consentimento e acesso a dados pessoais, sem expor senha, hash, token ou payload sensível. **Método/rota:** `GET /api/v1/auditoria`. **Permissão:** JWT `GERENTE` (restrito à própria `unidadeId`) ou `ADMIN` (qualquer unidade ou global). **Parâmetros:** query opcionais `unidadeId`, `acao`, `recurso`, `pedidoId`, `atorId`, `page`, `limit`.

Request: `GET /api/v1/auditoria?unidadeId=1&recurso=PEDIDO&page=1&limit=20`

Response 200:

```json
{
  "data": [
    {
      "auditoriaId": 15,
      "unidadeId": 1,
      "pedidoId": 5,
      "atorId": 102,
      "sujeitoUsuarioId": 100,
      "recurso": "PEDIDO",
      "acao": "TRANSICAO_PEDIDO",
      "resultado": "ENTREGUE",
      "requestId": "c2f86b2c-1c7e-4b02-9a91-81a9f1d0e5b4",
      "criadoEm": "2026-11-20T22:25:55Z"
    }
  ],
  "page": 1,
  "limit": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

**Códigos:** 200; 400 parâmetro ilegível; 401 sem token; 403 perfil sem permissão ou `GERENTE` tentando consultar outra unidade; 422 paginação ou identificador inválido.

## 14. Relatório consolidado de pedidos por unidade e canal

**Finalidade:** consolidar quantidade de pedidos, subtotal bruto dos itens, desconto de fidelidade, valor total líquido e pontos resgatados agrupados por `unidadeId` e `canalPedido`. **Método/rota:** `GET /api/v1/pedidos/relatorios/unidades-canais`. **Permissão:** JWT `GERENTE` (somente sua unidade) ou `ADMIN` (todas as unidades ou filtro específico). **Parâmetros:** query opcionais `unidadeId`, `canalPedido`, `status`.

Request: `GET /api/v1/pedidos/relatorios/unidades-canais?unidadeId=1`

Response 200:

```json
{
  "unidadeFiltro": 1,
  "canalFiltro": null,
  "statusFiltro": null,
  "totalPedidos": 8,
  "subtotalItensTotal": 478.80,
  "descontoFidelidadeTotal": 10.00,
  "valorTotal": 468.80,
  "pontosResgatadosTotal": 200,
  "itens": [
    {
      "unidadeId": 1,
      "canalPedido": "APP",
      "quantidadePedidos": 8,
      "subtotalItens": 478.80,
      "descontoFidelidade": 10.00,
      "valorTotal": 468.80,
      "pontosResgatados": 200
    }
  ]
}
```

**Códigos:** 200; 400 query ilegível; 401 sem token; 403 perfil sem permissão ou `GERENTE` consultando outra unidade; 422 filtro `unidadeId`, `canalPedido` ou `status` inválido.

## Mapa de requisitos e futuras evidências

| Contratos | IDs da matriz | Verificação na tarefa 03 |
|---|---|---|
| Login e papéis | RF-01, RNF-01, LGP-03, TES-03 | 200/401/403 e ausência de senha no response |
| Unidades/cardápio | RF-02, RF-03 | Preço por unidade, 404 e paginação |
| Criar/listar/consultar pedido | RF-04, RF-06, MUL-01–02, API-02–05, TES-04–05 | 201/404/409/422, canal persistido e filtro correto |
| Pagamento/cancelamento/status | RF-05, RF-09, RNF-04, ENT-06, TES-06 | aprovação, recusa, falha, idempotência e transições |
| Estoque e auditoria | RF-06, RNF-02, LGP-04, TES-07 | saldo/movimento persistido e evento de ação sensível |

Swagger/OpenAPI da tarefa 03 deverá refletir somente endpoints efetivamente implementados, com estes exemplos e códigos ajustados ao comportamento real. A coleção Postman/Insomnia e o plano de ≥10 cenários (≥6 positivos, ≥4 negativos) pertencem às etapas de implementação/teste, conforme `TES-01–07`.
