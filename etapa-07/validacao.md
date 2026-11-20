# Etapa 07 — Fluxo A fechado

## Base validada

Antes de alterar código, foram conferidos `etapa-06/validacao.md`, o roteiro Back-end em `C:\Users\felip\Downloads\roteiro da atividade.pdf` (SHA-256 `DFEA9533ED57BF5BB67282800937B2C9C9A27AB6D34F82373C5D2DFF74588B17`) e os contratos da etapa 02. A API local respondeu `UP/UP`; `mvnw.cmd ... test` passou com **12 testes**, zero falhas. A etapa 06 já comprovava pedido persistido, preço copiado e uma reserva por criação, inclusive concorrente. Não havia pendência bloqueante. A decisão anterior de liberar reserva em falha técnica foi substituída pela exigência desta tarefa: resultado indeterminado retém estoque até reconciliação.

## Arquivos criados ou alterados

- `backend/src/main/java/br/com/raizes/pagamentos/application/{PaymentGateway,PagamentoService}.java`: porta e orquestração em duas transações separadas pela chamada ao gateway.
- `backend/src/main/java/br/com/raizes/pagamentos/infrastructure/MockPaymentGateway.java`: aprovação, recusa, indeterminação e consulta de reconciliação; guarda explícita contra chamada com transação aberta.
- `backend/src/main/java/br/com/raizes/pagamentos/domain/{CenarioMock,StatusPagamento}.java` e `infrastructure/jpa/{PagamentoEntity,PagamentoJpaRepository,PagamentoEventoEntity,PagamentoEventoJpaRepository}.java`: vocabulário, tentativa, chave, status, payloads e histórico de cada resposta.
- `backend/src/main/java/br/com/raizes/pedidos/{api/PedidoFluxoController,application/TransicionarPedidoService,application/ExpirarReservasService,domain/PedidoStatusMachine,domain/StatusPedido,infrastructure/jpa/PedidoEntity,infrastructure/jpa/PedidoJpaRepository}.java`: rotas, transições, cancelamento, expiração e bloqueio de pedido.
- `backend/src/main/java/br/com/raizes/estoque/application/ReservaEstoqueService.java`: ator de sistema nulo para expiração agendada, mantendo movimento e auditoria.
- `backend/src/main/java/br/com/raizes/{RaizesApplication,shared/infrastructure/SecurityConfiguration}.java`: agendamento e permissões por papel/escopo.
- `backend/src/main/resources/db/migration/V6__fluxo_pagamento_e_expiracao.sql`, `V7__cenario_mock_mais_longo.sql`, `V8__historico_respostas_pagamento.sql`, `V9__movimento_terminal_unico.sql`: estados, TTL, payloads, eventos e unicidade de baixa/liberação por item.
- `backend/src/test/java/br/com/raizes/pagamentos/FluxoPedidoIntegracaoTest.java`: oito cenários HTTP/PostgreSQL.
- `backend/src/main/resources/static/openapi.yaml`, `backend/README.md`, `backend/postman/raizes-fluxo-a.postman_collection.json`, `etapa-02/{contratos-api,modelagem-fluxo-a}.md`: contratos executáveis, coleção e diagramas atualizados.
- `etapa-07/validacao.md`: este relatório.

## Resultados e consistência

| Cenário | Pedido / pagamento | Estoque e repetição |
|---|---|---|
| Aprovação | `AGUARDANDO_PAGAMENTO` → `EM_PREPARO` / `APROVADO`; depois `PRONTO` → `ENTREGUE` por papéis autorizados | Uma `RESERVA` e uma `BAIXA` por item; físico e reservado reduzem. Replay da chave, pagamentos concorrentes e reconciliações concorrentes não acrescentam baixa/evento. |
| Recusa | `PAGAMENTO_RECUSADO` / `RECUSADO` | Uma `LIBERACAO`, nenhuma `BAIXA`; físico preservado. Outra chave/cenário recebe 409. |
| Indeterminação | `PAGAMENTO_INDETERMINADO` / `INDETERMINADO`, HTTP 202 | Reserva e físico preservados; cancelamento e expiração não liberam. Reconciliação autorizada resolve para aprovação/baixa ou recusa/liberação; resposta ainda incerta mantém reserva. |
| Cancelamento pré-pagamento | `CANCELADO`, sem tentativa | Uma liberação; segunda chamada retorna 409 sem segundo movimento. |
| Expiração | Após 30 minutos sem tentativa, `CANCELADO` por tarefa agendada | A busca exclui pedidos com tentativa pendente; o processamento bloqueia e revalida ausência de pagamento antes de liberar. Chamada repetida não altera saldo. Teste com uma tentativa pendente e outro pedido vencido confirmou que só o segundo expira. |

O gateway recebe a solicitação **fora** de transação: a tentativa `PENDENTE` é confirmada no banco antes da chamada; o adaptador verifica que não há transação ativa. A conclusão bloqueia o pedido e grava pagamento, status, movimento, evento e auditoria em uma transação. Resposta técnica inesperada também é tratada como indeterminada. `PAGAMENTO_FALHOU` permanece apenas como valor legado do esquema, sem nova transição.

## Comandos e evidências

| Verificação | Resultado |
|---|---|
| `mvnw.cmd -o -Dmaven.repo.local=C:\projeto-raizes\backend\.mvn\local-repository test` antes de alterações | 12 testes, 0 falhas/erros. |
| `mvnw.cmd -o -Dmaven.repo.local=C:\projeto-raizes\backend\.mvn\local-repository clean verify` final | **BUILD SUCCESS**; 82 fontes Java, 5 fontes de teste; **20 testes**, 0 falhas/erros; JAR empacotado. |
| Inicialização local do JAR | Flyway aplicou V6, V7, V8 e V9 em `raizes`, esquema agora em v9; JPA validou; Tomcat iniciou em 8080. |
| `GET /api/v1/health` | `status=UP`, `database=UP`. |
| HTTP local + SQL, pedidos 2/3/4 | Pedido 2: `EM_PREPARO/APROVADO`, 1 reserva, 1 baixa, 0 liberação, 1 evento. Pedido 3: `PAGAMENTO_RECUSADO/RECUSADO`, 1 reserva, 0 baixa, 1 liberação, 1 evento. Pedido 4: inicialmente HTTP 202 `PAGAMENTO_INDETERMINADO/INDETERMINADO`; reconciliação HTTP 200 `EM_PREPARO/APROVADO`, 1 baixa e 2 eventos. |
| Unidade | Após os pedidos de demonstração, unidade 1/produto 10: físico 28, reservado 2 (reserva anterior da etapa 06); unidade 2/produto 10: físico 25, reservado 0. Nenhuma alteração cruzada. |
| Expiração agendada real | O pedido 1 da etapa 06 venceu às 18:51:28 e passou para `CANCELADO` sem pagamento; consulta posterior mostrou físico 28 e reservado 0 na unidade 1. A reserva anterior foi liberada, sem afetar os pedidos pagos. |
| Banco de teste | Após os testes: 0 pedidos, 0 pagamentos, 0 eventos e 0 movimentos associados a pedido. |
| Documentação | OpenAPI 3.0.3 analisado com SnakeYAML 2.6, 17 caminhos; coleção Postman JSON válida com 22 requisições encadeadas. |

## Pendências para a etapa 08

- Um gateway que permaneça indeterminado indefinidamente requer investigação operacional externa. A reserva é deliberadamente mantida; o mock `FALHA_TECNICA`/`INDETERMINADO_PENDENTE` não inventa uma recusa. Cenários de reconciliação resolúvel estão cobertos e testados.
- Requisitos de fidelidade e promoções seguem como documentação conceitual na matriz; não entram no preço/pagamento deste MVP. Publicação do repositório público e evidências finais de entrega permanecem fora desta etapa local.
- O cache local mantém `spring-boot-flyway` 4.1.0 com Boot 4.1.2; Flyway 12.4 avisa que PostgreSQL 19 é mais novo que a última versão verificada (18). Migrations, testes e início passaram.

**Resultado de passagem:** o Fluxo A está fechado e executável em PostgreSQL local. Pedido, tentativa, resposta, status e estoque permanecem consistentes nos três resultados do gateway, no cancelamento e na expiração. A API local ficou iniciada em `127.0.0.1:8080` para a tarefa 08.
