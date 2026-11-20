# Etapa 10 — Validação do Plano de Testes, suíte JUnit e coleção Postman

## 1. Validação prévia da tarefa 09

Antes de alterar código, foram conferidos `etapa-09/validacao.md` e a seção **"PLANO DE TESTES (OBRIGATÓRIO)"** em `C:\Users\felip\Downloads\roteiro da atividade.pdf` (`p. 11–12, 16–17`):

- Os **28 testes** existentes da tarefa 09 (`AuditoriaERelatorioIntegracaoTest` 3, `EstoqueIntegracaoTest` 3, `FidelidadeIntegracaoTest` 5, `AutorizacaoIntegracaoTest` 4, `FluxoPedidoIntegracaoTest` 8, `PedidoAccessPolicyTest` 1, `PedidoIntegracaoTest` 4) estavam passando contra o banco PostgreSQL local dedicado `raizes_test`, sem Docker nem Testcontainers.
- Não havia pendência bloqueante da tarefa 09.

## 2. Arquivos criados ou alterados

- `backend/src/test/java/br/com/raizes/pedidos/PedidoAccessPolicyTest.java`: ampliado de 1 para **3 testes unitários de domínio** (autorização de pagamento do `TOTEM` na própria unidade, política de consulta `PedidoAccessPolicy` com isolamento por dono/unidade/ADMIN e exaustão completa da máquina de estados `PedidoStatusMachine`).
- `backend/src/test/java/br/com/raizes/fidelidade/RegraFidelidadeTest.java`: **2 testes unitários de domínio** cobrindo acúmulo `1 ponto por R$ 1,00 inteiro`, desconto `100 pontos = R$ 5,00`, variação de saldo por `TipoMovimentoFidelidade` e validação de formato e teto de `50%` do subtotal.
- `backend/src/test/java/br/com/raizes/estoque/EstoqueDomainTest.java`: **2 testes unitários de domínio** cobrindo `EstoqueUnidadePolicy` e invariantes de `EstoqueSaldoEntity` (`disponivel = fisico - reservado`, bloqueio de saída ou reserva acima do disponível e bloqueio de baixa/liberação acima do reservado).
- `backend/src/test/java/br/com/raizes/pedidos/PedidoIntegracaoTest.java`: adicionado o teste de integração concorrente `doisPedidosConcorrentesDisputandoOUltimoItemPermitemApenasUmaReserva`, no qual dois clientes disputam simultaneamente a última unidade física disponível (`fisico = 1, reservado = 0`) e o bloqueio pessimista garante exatamente um `201 Created` e um `409 ESTOQUE_INSUFICIENTE`.
- `backend/postman/raizes-fluxo-a.postman_collection.json`: coleção Postman v2.1 reestruturada em **7 pastas** (`Auth`, `Produtos e Estoque`, `Pedidos`, `Pagamentos`, `Fidelidade`, `Auditoria e Relatórios`, `Erros`) com **38 requisições** encadeadas por scripts de pré-requisição e teste, contendo todos os 18 cenários (`T01`..`T18`) nomeados de forma 1:1.
- `etapa-10/plano-de-testes.md` e `backend/README.md`: documentação do ambiente PostgreSQL local dedicado (`raizes` e `raizes_test`), seed `R__demo_seed.sql`, contas/tokens, ordem de execução das pastas, matriz de cobertura da seção 8.3 e tabela completa com 18 cenários (11 positivos e 7 negativos).
- `etapa-10/validacao.md`: este relatório.

## 3. Comandos e resultados executados

| Comando / verificação | Resultado |
|---|---|
| `mvnw.cmd -o -Dmaven.repo.local=C:\projeto-raizes\backend\.mvn\local-repository clean verify` (com `SPRING_PROFILES_ACTIVE=test` contra `raizes_test`) | **BUILD SUCCESS**; 93 fontes Java de produção, **9 classes de teste**; **35 testes** executados (`7` unitários de domínio + `28` de integração HTTP/PostgreSQL), **0 falhas, 0 erros, 0 ignorados**; JAR empacotado. |
| Execução sequencial das 38 requisições de `postman/raizes-fluxo-a.postman_collection.json` contra a API local (`127.0.0.1:8080`, banco `raizes`) | **38/38 requisições aprovadas** com os códigos HTTP e payloads esperados (`200`, `201`, `202`, `401`, `403`, `404`, `409`, `422`). |

### Detalhamento das 9 classes da suíte JUnit (35 testes)

| Classe | Tipo | Testes | Cobertura |
|---|---|---|---|
| `PedidoAccessPolicyTest` | Unitário (domínio) | 3 | Escopo de pagamento/consulta por dono/unidade/ADMIN e máquina de estados `PedidoStatusMachine` |
| `RegraFidelidadeTest` | Unitário (domínio) | 2 | Cálculo de pontos por real pago, blocos de 100 pontos = R\$ 5,00 e teto de 50% do subtotal |
| `EstoqueDomainTest` | Unitário (domínio) | 2 | `EstoqueUnidadePolicy` e invariantes de `EstoqueSaldoEntity` |
| `AutorizacaoIntegracaoTest` | Integração (`raizes_test`) | 4 | Cadastro `CLIENTE`, hash BCrypt, login JWT, revogação de token por inativação/mudança de perfil, `401`/`403`/`404` por pedido/unidade |
| `EstoqueIntegracaoTest` | Integração (`raizes_test`) | 3 | Catálogo/cardápio por unidade sem N+1, entrada/saída manual protegendo reserva e atomicidade de reserva/liberação/baixa |
| `PedidoIntegracaoTest` | Integração (`raizes_test`) | 5 | Criação, cópia de preço, filtro por canal/status, pedido anônimo no totem, idempotência concorrente e **concorrência pelo último item disponível** |
| `FluxoPedidoIntegracaoTest` | Integração (`raizes_test`) | 8 | Pagamento aprovado → preparo → pronto → entregue, recusa, resultado indeterminado (`202`) + reconciliação, concorrência de pagamento/reconciliação, cancelamento e expiração seletiva |
| `FidelidadeIntegracaoTest` | Integração (`raizes_test`) | 5 | Pedido anônimo/sem consentimento, revogação antes da entrega, acúmulo exclusivo em `ENTREGUE` sem duplicidade, resgate com limite de 50%, estorno em recusa/reconciliação/cancelamento/expiração e isolamento por cliente |
| `AuditoriaERelatorioIntegracaoTest` | Integração (`raizes_test`) | 3 | Eventos de auditoria sem dados sensíveis, relatório consolidado por unidade/canal e restrição a `GERENTE`/`ADMIN` |

## 4. Correspondência 1:1 entre os 18 cenários da tabela e a coleção Postman executada

| ID | Cobertura 8.3 | Método e rota | Status obtido | Pasta / Nome exato na coleção `raizes-fluxo-a.postman_collection.json` |
|---|---|---|---|---|
| **T01** | 8.3.a.a (Positivo) | `POST /api/v1/auth/login` | `200 OK` | `Auth / T01 - Login cliente válido` |
| **T02** | Positivo | `GET /api/v1/unidades/1/cardapio?page=1&limit=20` | `200 OK` | `Produtos e Estoque / T02 - Consultar cardápio da unidade 1` |
| **T03** | 8.3.c.a (Positivo) | `POST /api/v1/pedidos` | `201 Created` | `Pedidos / T03 - Criar pedido válido no APP` |
| **T04** | 8.3.d.a (Positivo) | `POST /api/v1/pedidos/{{pedidoAprovado}}/pagamentos` | `200 OK` | `Pagamentos / T04 - Aprovar pagamento mock` |
| **T05** | 8.3.d.a (Positivo) | `PATCH /api/v1/pedidos/{{pedidoAprovado}}/status` | `200 OK` | `Pagamentos / T05 - Marcar pedido PRONTO` |
| **T06** | 8.3.d.a (Positivo) | `PATCH /api/v1/pedidos/{{pedidoAprovado}}/status` | `200 OK` | `Pagamentos / T06 - Marcar pedido ENTREGUE e acumular pontos` |
| **T07** | Positivo | `GET /api/v1/fidelidade/saldo` | `200 OK` | `Fidelidade / T07 - Consultar saldo de fidelidade após entrega` |
| **T08** | Positivo | `POST /api/v1/pedidos` | `201 Created` | `Fidelidade / T08 - Criar pedido com resgate de 100 pontos` |
| **T09** | Positivo | `GET /api/v1/pedidos?canalPedido=APP&status=ENTREGUE&page=1&limit=20` | `200 OK` | `Auditoria e Relatórios / T09 - Listar pedidos filtrando por canal e status` |
| **T10** | 8.3.a.b (Negativo) | `GET /api/v1/pedidos/{{pedidoAprovado}}` | `401 Unauthorized` | `Erros / T10 - Acesso sem token (401)` |
| **T11** | 8.3.a.c (Negativo) | `GET /api/v1/auditoria` | `403 Forbidden` | `Erros / T11 - Perfil sem permissão (403)` |
| **T12** | 8.3.b.a (Negativo) | `POST /api/v1/pedidos` | `422 Unprocessable Entity` | `Erros / T12 - Campo obrigatório canalPedido ausente (422)` |
| **T13** | 8.3.b.b (Negativo) | `POST /api/v1/pedidos` | `422 Unprocessable Entity` | `Erros / T13 - Formato de e-mail e quantidade inválidos (422)` |
| **T14** | 8.3.c.b (Negativo) | `POST /api/v1/pedidos` | `404 Not Found` | `Erros / T14 - Pedido em unidade inexistente (404)` |
| **T15** | 8.3.c.c (Negativo) | `POST /api/v1/pedidos` | `409 Conflict` | `Erros / T15 - Pedido com estoque insuficiente (409)` |
| **T16** | 8.3.d.b (Negativo) | `POST /api/v1/pedidos/{{pedidoRecusado}}/pagamentos` | `200 OK` (`RECUSADO`) | `Pagamentos / T16 - Pagamento mock recusado` |
| **T17** | 8.3.e.a (Positivo) | `GET /api/v1/auditoria?unidadeId=1&recurso=PEDIDO&page=1&limit=20` | `200 OK` | `Auditoria e Relatórios / T17 - Consultar trilha de auditoria da unidade` |
| **T18** | Positivo | `GET /api/v1/pedidos/relatorios/unidades-canais?unidadeId=1` | `200 OK` | `Auditoria e Relatórios / T18 - Relatório de pedidos por unidade e canal` |

## 5. Pendências para a tarefa 11

- Nenhuma pendência técnica bloqueante.
- Consolidação final dos entregáveis de documentação, histórico Git/repositório público e documento acadêmico ABNT (com a declaração de uso de IA) pertencem à próxima etapa.

