# Etapa 10 — Plano de Testes Obrigatório (Seção 8 do roteiro)

Base: seção **"PLANO DE TESTES (OBRIGATÓRIO)"** em `C:\Users\felip\Downloads\roteiro da atividade.pdf` (`p. 11–12, 16–17`). Não utiliza Docker nem Testcontainers; toda a suíte de integração roda contra o banco PostgreSQL local dedicado `raizes_test`, enquanto a coleção Postman executa contra o banco local `raizes`.

## 1. Ambiente, seed, autenticação por token e ordem de execução

### 1.1 Ambiente e bancos PostgreSQL locais

| Perfil Spring | Banco PostgreSQL local | Migrations e seed aplicadas pelo Flyway | Finalidade |
|---|---|---|---|
| `test` (`application-test.yml`) | `jdbc:postgresql://localhost:5432/raizes_test` | `V1` a `V11` + `R__demo_seed.sql` | Banco dedicado para os testes de integração JUnit (`mvnw.cmd clean verify`); cada classe restaura saldos e remove seus próprios registros em `@AfterEach`. |
| `local` (`application-local.yml`) | `jdbc:postgresql://localhost:5432/raizes` | `V1` a `V11` + `R__demo_seed.sql` | Execução local da API (`http://127.0.0.1:8080`), Swagger (`/swagger-ui.html`) e execução da coleção Postman (`backend/postman/raizes-fluxo-a.postman_collection.json`). |

### 1.2 Dados da seed (`R__demo_seed.sql`) e contas para obtenção de token

- **Unidades ativas:** `1` (`Unidade Centro`) e `2` (`Unidade Orla`).
- **Cardápio e estoque inicial:** Unidade 1 possui produtos `10` (`R$ 39,90`, saldo 30), `11` (`R$ 12,50`, saldo 50) e `12` (`R$ 24,00`, saldo 20); Unidade 2 possui produtos `10` (`R$ 42,00`, saldo 25) e `11` (`R$ 13,00`, saldo 40).
- **Contas fictícias locais** (todas com senha `Demo@1234`, gravada como hash BCrypt):
  - `cliente@raizes.invalid` (ID `100`, `CLIENTE`, global) e `cliente2@raizes.invalid` (ID `108`, `CLIENTE`, global);
  - `totem@raizes.invalid` (ID `101`, `TOTEM`, unidade `1`);
  - `atendente@raizes.invalid` (ID `102`, `ATENDENTE`, unidade `1`) e `atendente.orla@raizes.invalid` (ID `106`, `ATENDENTE`, unidade `2`);
  - `cozinha@raizes.invalid` (ID `103`, `COZINHA`, unidade `1`);
  - `gerente@raizes.invalid` (ID `104`, `GERENTE`, unidade `1`) e `gerente.orla@raizes.invalid` (ID `107`, `GERENTE`, unidade `2`);
  - `admin@raizes.invalid` (ID `105`, `ADMIN`, global).

### 1.3 Ordem de execução da coleção Postman (`raizes-fluxo-a.postman_collection.json`)

Importe `backend/postman/raizes-fluxo-a.postman_collection.json` no Postman (ou Insomnia) com a API ativa em `http://127.0.0.1:8080` (`baseUrl`) e execute as **7 pastas** na ordem abaixo (38 requisições encadeadas automaticamente por variáveis de coleção):

1. **`Auth` (5 requisições):** autentica `cliente`, `cozinha`, `atendente`, `gerente` e `totem` e popula `{{clienteToken}}`, `{{cozinhaToken}}`, `{{atendenteToken}}`, `{{gerenteToken}}` e `{{totemToken}}`.
2. **`Produtos e Estoque` (3 requisições):** valida `/api/v1/health`, consulta o cardápio paginado da unidade 1 (`T02`) e registra entrada de estoque (`+10` no produto 10 da unidade 1) para garantir saldo suficiente em múltiplas execuções.
3. **`Pedidos` (8 requisições):** ativa o consentimento de fidelidade, cria pedido válido no APP (`T03`), cria pedidos para recusa, reconciliação e cancelamento, cancela o pedido pendente e cria/acompanha pedido anônimo no totem.
4. **`Pagamentos` (7 requisições):** aprova o pagamento mock (`T04`), consulta o pedido pago, avança para `PRONTO` (`T05`) e `ENTREGUE` (`T06`), executa pagamento recusado (`T16`) e pagamento indeterminado (`202`) seguido de reconciliação (`200`).
5. **`Fidelidade` (6 requisições):** consulta o saldo acumulado após entrega (`T07`), cria pedido posterior com resgate de `100` pontos = `R$ 5,00` (`T08`), estorna os pontos via recusa de pagamento, consulta o histórico paginado e exercita revogação e reativação do consentimento.
6. **`Auditoria e Relatórios` (3 requisições):** filtra pedidos por `canalPedido=APP&status=ENTREGUE` (`T09`), consulta a trilha de auditoria (`T17`) e o relatório consolidado por unidade e canal (`T18`).
7. **`Erros` (6 requisições):** valida os cenários negativos `401` (`T10`), `403` (`T11`), `422` campo obrigatório ausente (`T12`), `422` tipo/valor inválido (`T13`), `404` unidade inexistente (`T14`) e `409` estoque insuficiente (`T15`).

## 2. Cobertura obrigatória da seção 8.3 do roteiro

| Item da seção 8.3 do roteiro | Cenários da tabela | Testes automatizados JUnit correspondentes |
|---|---|---|
| **a.a)** Autenticação: Login (token válido) | `T01` | `AutorizacaoIntegracaoTest.cadastroConsentimentoELoginNaoExibemSenha` |
| **a.b)** Acesso sem token (`401`) | `T10` | `AutorizacaoIntegracaoTest.credenciaisInvalidasTokenAusenteEAlteradoRetornam401`, `PedidoIntegracaoTest.rejeitaErrosSemPersistirOuReservar` |
| **a.c)** Acesso com perfil sem permissão (`403`) | `T11` | `AutorizacaoIntegracaoTest.pedidoExigePropriedadePerfilEUnidadeCorretos`, `AuditoriaERelatorioIntegracaoTest.autorizacaoRestringeAuditoriaERelatorioAGerenteDaUnidadeOuAdmin`, `PedidoAccessPolicyTest`, `EstoqueDomainTest` |
| **b.a)** Validação: campo obrigatório ausente (`400/422`) | `T12` | `PedidoIntegracaoTest.rejeitaErrosSemPersistirOuReservar`, `EstoqueIntegracaoTest.movimentoManualAfetaSomenteUnidadeEProtegeReserva` |
| **b.b)** Validação: tipo/formato inválido (`400/422`) | `T13` | `AutorizacaoIntegracaoTest.credenciaisInvalidasTokenAusenteEAlteradoRetornam401`, `FidelidadeIntegracaoTest.acumulaSoAposEntregueImpedeCreditoDuplicadoEResgataComDescontoELimite`, `RegraFidelidadeTest` |
| **c.a)** Fluxo principal: pedido com itens válidos → criado (`201`) | `T03`, `T08` | `PedidoIntegracaoTest.criaPersisteReservaUmaVezFiltraEConservaPreco`, `PedidoIntegracaoTest.duasCriacoesConcorrentesDaMesmaChaveReservamUmaVez` |
| **c.b)** Fluxo principal: pedido com produto/unidade inexistente (`404`) | `T14` | `EstoqueIntegracaoTest`, `PedidoIntegracaoTest` |
| **c.c)** Fluxo principal: pedido com estoque insuficiente (`409`) e disputa pelo último item | `T15` | `PedidoIntegracaoTest.doisPedidosConcorrentesDisputandoOUltimoItemPermitemApenasUmaReserva`, `PedidoIntegracaoTest.rejeitaErrosSemPersistirOuReservar`, `EstoqueDomainTest` |
| **d.a)** Pagamento mock aprovado → status atualizado (`EM_PREPARO` → `PRONTO` → `ENTREGUE`) | `T04`, `T05`, `T06` | `FluxoPedidoIntegracaoTest.aprovacaoBaixaUmaVezEAvancaAteEntrega`, `FluxoPedidoIntegracaoTest.pagamentosConcorrentesComMesmaChaveBaixamUmaVez` |
| **d.b)** Pagamento mock recusado → status e mensagem coerentes (`PAGAMENTO_RECUSADO`) | `T16` | `FluxoPedidoIntegracaoTest.recusaLiberaReservaSemBaixaOuSegundaTentativa`, `FluxoPedidoIntegracaoTest.indeterminadoMantemReservaAteReconciliacaoDeRecusaOuAprovacao` |
| **e.a)** Logs/auditoria: ação sensível gera registro verificável | `T17` | `AuditoriaERelatorioIntegracaoTest.registraEventosDePedidoStatusCancelamentoEstoqueEDadosPessoaisSemDadosSensiveis` |

## 3. Tabela de cenários de teste (18 cenários: 11 positivos e 7 negativos)

| ID | Tipo | Método e rota | Pré-condição | Entrada (headers / query / body) | Saída esperada (status e resposta) | Nome exato da requisição na coleção (`Pasta / Requisição`) |
|---|---|---|---|---|---|---|
| **T01** | Positivo | `POST /api/v1/auth/login` | Usuário `cliente@raizes.invalid` presente na seed `R__demo_seed.sql` | `{"email":"cliente@raizes.invalid","senha":"Demo@1234"}` | `200 OK`; `accessToken` JWT de 3 segmentos, `tokenType="Bearer"`, `expiresIn=3600`, `user.id=100`, `user.perfil="CLIENTE"` | `Auth / T01 - Login cliente válido` |
| **T02** | Positivo | `GET /api/v1/unidades/1/cardapio?page=1&limit=20` | Unidade 1 ativa com produtos 10, 11 e 12 no cardápio | Query `page=1&limit=20` | `200 OK`; `totalItems=3`, `data[0].produtoId=10`, `preco=39.90`, `disponivel=true` | `Produtos e Estoque / T02 - Consultar cardápio da unidade 1` |
| **T03** | Positivo | `POST /api/v1/pedidos` | `clienteToken` válido, consentimento de fidelidade ativo e saldo disponível para o produto 10 | Header `Idempotency-Key: <uuid>`; `{"unidadeId":1,"canalPedido":"APP","modalidadeAtendimento":"RETIRADA","formaPagamento":"MOCK","itens":[{"produtoId":10,"quantidade":3}]}` | `201 Created`; `status="AGUARDANDO_PAGAMENTO"`, `subtotalItens=119.70`, `descontoFidelidade=0.00`, `participaFidelidade=true`, `total=119.70` | `Pedidos / T03 - Criar pedido válido no APP` |
| **T04** | Positivo | `POST /api/v1/pedidos/{id}/pagamentos` | Pedido `{{pedidoAprovado}}` em `AGUARDANDO_PAGAMENTO` | Header `Idempotency-Key: <uuid>`; `{"cenarioMock":"APROVAR"}` | `200 OK`; `statusPagamento="APROVADO"`, `statusPedido="EM_PREPARO"`, `valor=119.70`, `codigoMock="MOCK-OK"`, `mensagem="Pagamento simulado aprovado."` | `Pagamentos / T04 - Aprovar pagamento mock` |
| **T05** | Positivo | `PATCH /api/v1/pedidos/{id}/status` | Pedido `{{pedidoAprovado}}` em `EM_PREPARO`; `cozinhaToken` da unidade 1 | `{"status":"PRONTO"}` | `200 OK`; `statusAnterior="EM_PREPARO"`, `status="PRONTO"`, `reservaLiberada=false` | `Pagamentos / T05 - Marcar pedido PRONTO` |
| **T06** | Positivo | `PATCH /api/v1/pedidos/{id}/status` | Pedido `{{pedidoAprovado}}` em `PRONTO`; `atendenteToken` da unidade 1 | `{"status":"ENTREGUE"}` | `200 OK`; `statusAnterior="PRONTO"`, `status="ENTREGUE"` (credita `119` pontos na conta do cliente 100) | `Pagamentos / T06 - Marcar pedido ENTREGUE e acumular pontos` |
| **T07** | Positivo | `GET /api/v1/fidelidade/saldo` | Pedido `ENTREGUE` com consentimento ativo (`clienteToken`) | Header `Authorization: Bearer {{clienteToken}}` | `200 OK`; `clienteId=100`, `consentimentoAtivo=true`, `saldo>=119`, `saldoCalculadoMovimentos=saldo`, `saldoConsistente=true` | `Fidelidade / T07 - Consultar saldo de fidelidade após entrega` |
| **T08** | Positivo | `POST /api/v1/pedidos` | `clienteToken` com `saldo >= 100` e consentimento ativo | Header `Idempotency-Key: <uuid>`; `{"unidadeId":1,"canalPedido":"APP","modalidadeAtendimento":"RETIRADA","formaPagamento":"MOCK","pontosResgate":100,"itens":[{"produtoId":10,"quantidade":1}]}` | `201 Created`; `subtotalItens=39.90`, `pontosResgatados=100`, `descontoFidelidade=5.00`, `total=34.90` | `Fidelidade / T08 - Criar pedido com resgate de 100 pontos` |
| **T09** | Positivo | `GET /api/v1/pedidos?canalPedido=APP&status=ENTREGUE&page=1&limit=20` | Ao menos um pedido `APP` entregue do cliente autenticado | Query `canalPedido=APP&status=ENTREGUE&page=1&limit=20` | `200 OK`; `totalItems>=1`, todos os elementos de `data` com `canalPedido="APP"` e `status="ENTREGUE"` | `Auditoria e Relatórios / T09 - Listar pedidos filtrando por canal e status` |
| **T10** | Negativo | `GET /api/v1/pedidos/{id}` | Pedido existente, requisição sem cabeçalho `Authorization` | Sem token | `401 Unauthorized`; `error="NAO_AUTENTICADO"`, `message="Autenticação necessária."`, `requestId` UUID | `Erros / T10 - Acesso sem token (401)` |
| **T11** | Negativo | `GET /api/v1/auditoria` | Usuário autenticado com perfil `CLIENTE` (`clienteToken`) | Header `Authorization: Bearer {{clienteToken}}` | `403 Forbidden`; `error="ACESSO_NEGADO"`, `message="Acesso não permitido."`, `requestId` UUID | `Erros / T11 - Perfil sem permissão (403)` |
| **T12** | Negativo | `POST /api/v1/pedidos` | `clienteToken` válido; JSON sem campo obrigatório `canalPedido` | Header `Idempotency-Key: <uuid>`; `{"unidadeId":1,"modalidadeAtendimento":"RETIRADA","formaPagamento":"MOCK","itens":[{"produtoId":10,"quantidade":1}]}` | `422 Unprocessable Entity`; `error="CANAL_PEDIDO_INVALIDO"`, `requestId` UUID | `Erros / T12 - Campo obrigatório canalPedido ausente (422)` |
| **T13** | Negativo | `POST /api/v1/pedidos` | `clienteToken` válido; item com `quantidade = -2` inválida | Header `Idempotency-Key: <uuid>`; `{"unidadeId":1,"canalPedido":"APP","modalidadeAtendimento":"RETIRADA","formaPagamento":"MOCK","itens":[{"produtoId":10,"quantidade":-2}]}` | `422 Unprocessable Entity`; `error="ITEM_INVALIDO"`, `requestId` UUID | `Erros / T13 - Formato de e-mail e quantidade inválidos (422)` |
| **T14** | Negativo | `POST /api/v1/pedidos` | `clienteToken` válido; `unidadeId = 999` inexistente | Header `Idempotency-Key: <uuid>`; `{"unidadeId":999,"canalPedido":"APP","modalidadeAtendimento":"RETIRADA","formaPagamento":"MOCK","itens":[{"produtoId":10,"quantidade":1}]}` | `404 Not Found`; `error="UNIDADE_NAO_ENCONTRADA"`, `requestId` UUID | `Erros / T14 - Pedido em unidade inexistente (404)` |
| **T15** | Negativo | `POST /api/v1/pedidos` | `clienteToken` válido; `quantidade = 999` superior ao estoque disponível da unidade 1 | Header `Idempotency-Key: <uuid>`; `{"unidadeId":1,"canalPedido":"APP","modalidadeAtendimento":"RETIRADA","formaPagamento":"MOCK","itens":[{"produtoId":10,"quantidade":999}]}` | `409 Conflict`; `error="ESTOQUE_INSUFICIENTE"`, `requestId` UUID (nenhum pedido ou reserva gravados) | `Erros / T15 - Pedido com estoque insuficiente (409)` |
| **T16** | Negativo (recusa mock) | `POST /api/v1/pedidos/{id}/pagamentos` | Pedido `{{pedidoRecusado}}` em `AGUARDANDO_PAGAMENTO` | Header `Idempotency-Key: <uuid>`; `{"cenarioMock":"RECUSAR"}` | `200 OK`; `statusPagamento="RECUSADO"`, `statusPedido="PAGAMENTO_RECUSADO"`, `codigoMock="MOCK-DECLINED"`, `mensagem="Pagamento simulado recusado."` (reserva liberada) | `Pagamentos / T16 - Pagamento mock recusado` |
| **T17** | Positivo | `GET /api/v1/auditoria?unidadeId=1&recurso=PEDIDO&page=1&limit=20` | `gerenteToken` da unidade 1 após ações sensíveis em pedidos | Query `unidadeId=1&recurso=PEDIDO&page=1&limit=20` | `200 OK`; `totalItems>=1`, eventos com `unidadeId=1`, `recurso="PEDIDO"`, `acao`, `atorId`, `criadoEm` e sem senha/token | `Auditoria e Relatórios / T17 - Consultar trilha de auditoria da unidade` |
| **T18** | Positivo | `GET /api/v1/pedidos/relatorios/unidades-canais?unidadeId=1` | `gerenteToken` da unidade 1 com pedidos persistidos | Query `unidadeId=1` | `200 OK`; `unidadeFiltro=1`, `totalPedidos>=1`, `itens` agregados por `(unidadeId, canalPedido)` com somas consistentes | `Auditoria e Relatórios / T18 - Relatório de pedidos por unidade e canal` |

