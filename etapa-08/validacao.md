# Etapa 08 — Programa de fidelidade com consentimento LGPD

## 1. Validação prévia da tarefa 07

Antes de alterar o código, foi validado o estado deixado pela etapa 07 contra `C:\Users\felip\Downloads\roteiro da atividade.pdf`:

- `mvnw.cmd -o -Dmaven.repo.local=C:\projeto-raizes\backend\.mvn\local-repository test` executou **20 testes** (`EstoqueIntegracaoTest` 3, `AutorizacaoIntegracaoTest` 4, `FluxoPedidoIntegracaoTest` 8, `PedidoAccessPolicyTest` 1, `PedidoIntegracaoTest` 4), com **0 falhas e 0 erros**.
- Conferência SQL em `raizes` e `raizes_test` confirmou 0 pedidos inconsistentes (todo pedido pago possui exatamente uma `BAIXA` por item; todo pedido recusado ou cancelado possui exatamente uma `LIBERACAO` por item) e saldos físicos/reservados integralmente explicados por `movimento_estoque`.
- Não havia pendência bloqueante da tarefa 07.

## 2. Regras de fidelidade definidas e documentadas

| Regra | Definição implementada (`RegraFidelidade`) | Código de erro quando violada |
|---|---|---|
| Elegibilidade e consentimento LGPD | Somente `CLIENTE` autenticado com consentimento ativo para a finalidade `FIDELIDADE`. Pedidos anônimos (`TOTEM`/`BALCAO` sem `cliente_id`) ou criados sem consentimento vigente têm `participa_fidelidade = false` e não acumulam nem resgatam pontos. Se o consentimento for revogado antes da entrega, a transição para `ENTREGUE` não credita pontos. | `409 FIDELIDADE_EXIGE_CLIENTE` / `409 CONSENTIMENTO_FIDELIDADE_INATIVO` |
| Taxa de acúmulo | **1 ponto por cada R\$ 1,00 inteiro efetivamente pago** (`floor(total)`), creditado exclusivamente quando o pedido chega ao estado `ENTREGUE`. | — |
| Proteção contra crédito duplicado | Lock pessimista na conta (`fidelidade_conta`) + verificação prévia + constraint `UNIQUE (pedido_id, tipo)` em `fidelidade_movimento`. | `409 TRANSICAO_INVALIDA` na máquina de estados / retorno `0` idempotente no serviço |
| Taxa de resgate | Blocos de **100 pontos = R\$ 5,00** de desconto (`R$ 0,05` por ponto) informados em `pontosResgate` na criação de pedido posterior. | `422 PONTOS_RESGATE_INVALIDOS` (negativo, não múltiplo de 100 ou acima de 1.000.000) |
| Limite de desconto | Máximo de **50% do subtotal dos itens** (`descontoFidelidade <= subtotalItens * 0.50`), garantindo `total = subtotalItens - descontoFidelidade > 0` para o fluxo de pagamento. | `409 LIMITE_RESGATE_EXCEDIDO` / `409 SALDO_PONTOS_INSUFICIENTE` |
| Devolução de pontos resgatados | Quando um pedido com `pontos_resgatados > 0` vai para `PAGAMENTO_RECUSADO` (na solicitação ou na reconciliação) ou `CANCELADO` (cancelamento manual pré-pagamento ou expiração agendada), um lançamento `ESTORNO_RESGATE` devolve os pontos ao saldo uma única vez (`UNIQUE (pedido_id, tipo)`). | — |
| Autorização por cliente | `/api/v1/fidelidade/{consentimento,saldo,movimentacoes}` exige papel `CLIENTE` e opera exclusivamente sobre `principal.id()` do JWT; outros perfis recebem `403 ACESSO_NEGADO` e requisições sem token recebem `401 NAO_AUTENTICADO`. | `401 NAO_AUTENTICADO` / `403 ACESSO_NEGADO` |

## 3. Arquivos criados ou alterados

- `backend/src/main/resources/db/migration/V10__programa_fidelidade.sql`: colunas `desconto_fidelidade`, `pontos_resgatados` e `participa_fidelidade` em `pedido` com `ck_pedido_fidelidade`; tabelas `fidelidade_conta` e `fidelidade_movimento` com `uq_fidelidade_movimento_pedido_tipo UNIQUE (pedido_id, tipo)`.
- `backend/src/main/java/br/com/raizes/fidelidade/domain/{RegraFidelidade,TipoMovimentoFidelidade}.java`: taxas, limite de 50%, validações e variação algébrica do saldo (`+ACUMULO`, `-RESGATE`, `+ESTORNO_RESGATE`).
- `backend/src/main/java/br/com/raizes/fidelidade/infrastructure/jpa/{FidelidadeContaEntity,FidelidadeContaJpaRepository,FidelidadeMovimentoEntity,FidelidadeMovimentoJpaRepository}.java`: persistência JPA, criação idempotente de conta (`ON CONFLICT DO NOTHING`), lock pessimista e agregação por tipo.
- `backend/src/main/java/br/com/raizes/fidelidade/application/FidelidadeService.java` e `api/FidelidadeController.java`: casos de uso e rotas `GET/PUT /api/v1/fidelidade/consentimento`, `GET /api/v1/fidelidade/saldo` e `GET /api/v1/fidelidade/movimentacoes`.
- `backend/src/main/java/br/com/raizes/identidade/application/IdentidadeService.java`: consulta auditada do estado vigente de consentimento e registro explícito de `CONCEDIDO`/`REVOGADO` na auditoria.
- `backend/src/main/java/br/com/raizes/pedidos/{api/PedidoCriacaoController,application/CriarPedidoService,application/ConsultarPedidoService,application/TransicionarPedidoService,application/ExpirarReservasService,infrastructure/jpa/PedidoEntity}.java`: suporte a `pontosResgate`, exposição de `subtotalItens`, `descontoFidelidade`, `pontosResgatados`, `participaFidelidade` e `total`, acúmulo ao atingir `ENTREGUE` e devolução em cancelamento/expiração.
- `backend/src/main/java/br/com/raizes/pagamentos/application/PagamentoService.java`: devolução automática dos pontos resgatados quando o pagamento mock é `RECUSADO` (direto ou via reconciliação).
- `backend/src/main/java/br/com/raizes/shared/infrastructure/SecurityConfiguration.java`: autorização restrita a `ROLE_CLIENTE` para `/api/v1/fidelidade/**`.
- `backend/src/test/java/br/com/raizes/fidelidade/FidelidadeIntegracaoTest.java`: 5 testes ponta a ponta cobrindo pedido anônimo/sem consentimento, revogação antes da entrega, acúmulo exclusivo em `ENTREGUE`, bloqueio de crédito duplicado concorrente e direto, resgate com limite de 50%, devolução em recusa/reconciliação/cancelamento/expiração e isolamento por cliente/perfil.
- `backend/src/main/resources/static/openapi.yaml`, `backend/README.md`, `backend/postman/raizes-fluxo-a.postman_collection.json`, `etapa-02/{modelagem-fluxo-a,contratos-api}.md` e `etapa-08/validacao.md`: DER, contratos, Swagger (20 rotas), coleção Postman (28 requisições) e relatório atualizados.

## 4. Testes, comandos e conferência do saldo pelos movimentos

| Comando / verificação | Resultado |
|---|---|
| `mvnw.cmd -o -Dmaven.repo.local=C:\projeto-raizes\backend\.mvn\local-repository test` (baseline tarefa 07) | **20 testes**, 0 falhas/erros. |
| `mvnw.cmd -o -Dmaven.repo.local=C:\projeto-raizes\backend\.mvn\local-repository clean verify` (final tarefa 08) | **BUILD SUCCESS**; 90 fontes Java de produção, 6 classes de teste; **25 testes**, 0 falhas/erros; JAR empacotado. |
| Inicialização do JAR no perfil `local` + `GET /api/v1/health` | Flyway aplicou `V10__programa_fidelidade.sql` (esquema v10); Hibernate `ddl-auto=validate` aprovou; `status=UP`, `database=UP`. |
| Simulação HTTP + SQL no banco `raizes` (`cliente@raizes.invalid`, ID 100) | Pedido 5 (`R$ 119,70` entregue) → `+119` pts (saldo `119`). Pedido 6 (subtotal `R$ 39,90`, resgate `-100` pts = `R$ 5,00`, pago `R$ 34,90`, entregue) → `-100` pts (saldo `19`) e `+34` pts na entrega (saldo `53`). Pedido 7 (`R$ 79,80` entregue) → `+79` pts (saldo `132`). Pedido 8 (resgate `-100` pts, pagamento `RECUSADO`) → `-100` pts (saldo `32`) e `+100` pts de `ESTORNO_RESGATE` (saldo `132`). |
| Reconciliação algébrica (`fidelidade_conta` vs `fidelidade_movimento`) | `acumulos = 232`, `resgates = 200`, `estornos = 100` → `232 - 200 + 100 = 132 = fidelidade_conta.saldo` (`consistente = true` no SQL e `saldoConsistente = true` em `GET /api/v1/fidelidade/saldo`). |
| Banco `raizes_test` após a suíte | `0` pedidos, `0` contas e `0` movimentos de fidelidade residuais; saldos de estoque restaurados. |

## 5. Pendências

- Nenhuma pendência bloqueante para a tarefa 09.
- Promoções/campanhas (`RF-08`) permanecem como documentação conceitual conforme previsto na matriz da etapa 01.
- Publicação do repositório Git público e consolidação do documento final ABNT (incluindo a declaração de uso de IA exigida pelo roteiro) pertencem à etapa final de entrega.

