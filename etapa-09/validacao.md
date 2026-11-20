# Etapa 09 — LGPD, auditoria paginada, relatório por unidade/canal e promoções conceituais

## 1. Validação prévia da tarefa 08

Antes de iniciar a tarefa 09, foi validado o resultado da etapa 08 (`etapa-08/validacao.md`) e relida a seção obrigatória **"LGPD, PRIVACIDADE E SEGURANÇA NO BACK-END"** em `C:\Users\felip\Downloads\roteiro da atividade.pdf` (`p. 9, 15, 17`):

- Os **25 testes** existentes (`EstoqueIntegracaoTest` 3, `FidelidadeIntegracaoTest` 5, `AutorizacaoIntegracaoTest` 4, `FluxoPedidoIntegracaoTest` 8, `PedidoAccessPolicyTest` 1, `PedidoIntegracaoTest` 4) estavam passando sem falhas.
- O saldo de fidelidade do cliente de demonstração (`132` pontos) continuava integralmente explicado pelos movimentos persistidos (`232` acumulados - `200` resgatados + `100` estornados = `132`, `consistente = true`).
- Não havia pendência bloqueante da tarefa 08.

## 2. Completude da auditoria e conformidade LGPD

### 2.1 Eventos auditados (com ator, recurso, ação, unidade e horário, sem dados sensíveis)

A migration `V11__auditoria_unidade_e_indices.sql` adicionou `unidade_id` e índices de consulta à tabela `auditoria` (retropreenchendo eventos anteriores vinculados a `pedido_id`). Nenhum evento armazena senha, hash BCrypt, JWT, cabeçalho `Authorization` ou payload financeiro:

| Recurso | Ação | Resultado gravado | Unidade (`unidade_id`) | Quando ocorre |
|---|---|---|---|---|
| `USUARIO` | `CADASTRO_CLIENTE` | `SUCESSO` | `null` (cliente é global) | `POST /api/v1/clientes` |
| `USUARIO` | `LOGIN` | `SUCESSO` | `unidade_id` do operador ou `null` para `CLIENTE`/`ADMIN` | `POST /api/v1/auth/login` |
| `USUARIO` | `ACESSO_DADO_PESSOAL` | `SUCESSO` | `unidade_id` do usuário ou `null` | `GET /api/v1/usuarios/me` |
| `CONSENTIMENTO` | `CONSENTIMENTO_FIDELIDADE` | `CONCEDIDO` / `REVOGADO` | `null` | `PUT /api/v1/fidelidade/consentimento` e `/usuarios/me/consentimentos/fidelidade` |
| `CONSENTIMENTO` | `ACESSO_DADO_PESSOAL` | `SUCESSO` | `null` | `GET /api/v1/fidelidade/consentimento` |
| `FIDELIDADE` / `FIDELIDADE_HISTORICO` | `ACESSO_DADO_PESSOAL` | `SUCESSO` | `null` | `GET /api/v1/fidelidade/saldo` e `/movimentacoes` |
| `FIDELIDADE` | `RESGATE_FIDELIDADE` / `ESTORNO_RESGATE_FIDELIDADE` / `ACUMULO_FIDELIDADE` | `SUCESSO` | `pedido.unidade_id` | Criação com resgate, recusa/cancelamento/expiração e entrega (`ENTREGUE`) |
| `PEDIDO` | `CRIACAO_PEDIDO` | `SUCESSO` | `pedido.unidade_id` | `POST /api/v1/pedidos` |
| `PEDIDO` | `ACESSO_DADO_PESSOAL` | `SUCESSO` | `pedido.unidade_id` | `GET /api/v1/pedidos/{id}` |
| `PEDIDO` | `TRANSICAO_PEDIDO` | `PRONTO` / `ENTREGUE` | `pedido.unidade_id` | `PATCH /api/v1/pedidos/{id}/status` |
| `PEDIDO` | `CANCELAMENTO_PEDIDO` | `SUCESSO` | `pedido.unidade_id` | `POST /api/v1/pedidos/{id}/cancelamento` |
| `PEDIDO` | `EXPIRACAO_RESERVA` | `SUCESSO` | `pedido.unidade_id` (`ator_id = null`) | Tarefa agendada de expiração |
| `PAGAMENTO` | `RESULTADO_PAGAMENTO` | `APROVADO` / `RECUSADO` / `INDETERMINADO` | `pedido.unidade_id` | Solicitação e reconciliação de pagamento mock |
| `ESTOQUE` | `MOVIMENTO_ESTOQUE` | `ENTRADA` / `SAIDA_AJUSTE` | `unidadeId` do movimento | `POST /api/v1/estoque/movimentacoes` |

### 2.2 Documentação LGPD e evolução conceitual de promoções/campanhas

Documentadas nas seções **8** e **9** de `etapa-02/modelagem-fluxo-a.md` e resumidas em `backend/README.md`:

- **Inventário, finalidade e base legal considerada (Lei 13.709/2018):**
  - `nome` e `email`: identificação da conta e autenticação (`art. 7º, V` — execução de contrato). Não são coletados CPF, telefone, data de nascimento ou endereço.
  - `senha_hash`: verificação de credencial com hash irreversível `BCrypt` custo 12 (`art. 7º, V` e `art. 46`).
  - `consentimento`, `fidelidade_conta` e `fidelidade_movimento`: participação no programa de fidelidade mediante **consentimento específico, livre e revogável** (`art. 7º, I` e `art. 8º, § 5º`), registrado em log append-only (`finalidade = 'FIDELIDADE'`, `versao_texto = 'v1'`, `request_id`, `registrado_em`).
  - `pedido` e `item_pedido`: execução do pedido e obrigação fiscal/contábil (`art. 7º, II e V`), com compra presencial anônima (`cliente_id IS NULL`) acompanhada por `codigo_acompanhamento` aleatório de 144 bits sem dados pessoais.
  - `auditoria`: segurança e rastreabilidade do tratamento (`art. 7º, II`, `art. 37` e `art. 46`).
- **Retenção e anonimização:** anonimização in-place do registro em `usuario` (`ativo = FALSE`, `nome = 'Titular Anonimizado'`, `email = 'anonimizado+<id>@anon.raizes.invalid'`), invalidando tokens ativos e preservando a integridade referencial contábil/fiscal dos pedidos e da auditoria (que armazena apenas IDs numéricos).
- **Promoções e campanhas (`RF-08`, evolução conceitual):** modelo `CAMPANHA_PROMOCIONAL` com vigência, escopo por unidade/canal e ordem determinística de cálculo (`subtotalItens` → `descontoPromocional` → teto de 50% de `descontoFidelidade` sobre a base pós-promoção → `total` pago como base de acúmulo).

## 3. Arquivos criados ou alterados

- `backend/src/main/resources/db/migration/V11__auditoria_unidade_e_indices.sql`: coluna `unidade_id` em `auditoria`, backfill a partir de `pedido` e índices `(unidade_id, criado_em DESC, id DESC)` e `(acao, recurso, criado_em DESC, id DESC)`.
- `backend/src/main/java/br/com/raizes/auditoria/{api/AuditoriaController,application/ConsultarAuditoriaService,application/RegistrarAuditoriaService,infrastructure/jpa/AuditoriaEntity,infrastructure/jpa/AuditoriaJpaRepository}.java`: persistência de `unidade_id` e endpoint `GET /api/v1/auditoria` paginado e filtrado por `unidadeId`, `acao`, `recurso`, `pedidoId` e `atorId`, com escopo por unidade para `GERENTE` e global para `ADMIN`.
- `backend/src/main/java/br/com/raizes/pedidos/{api/PedidoConsultaController,application/RelatorioPedidosService,infrastructure/jpa/PedidoJpaRepository}.java`: endpoint `GET /api/v1/pedidos/relatorios/unidades-canais` com agregação por `(unidadeId, canalPedido)` e filtros opcionais `unidadeId`, `canalPedido` e `status`.
- `backend/src/main/java/br/com/raizes/{estoque/application/EstoqueService,fidelidade/application/FidelidadeService,identidade/application/IdentidadeService,pagamentos/application/PagamentoService,pedidos/application/CriarPedidoService,pedidos/application/ConsultarPedidoService,pedidos/application/TransicionarPedidoService,pedidos/application/ExpirarReservasService,shared/infrastructure/SecurityConfiguration}.java`: propagação de `unidadeId` e resultado detalhado nos eventos de auditoria e regras de segurança HTTP.
- `backend/src/test/java/br/com/raizes/auditoria/AuditoriaERelatorioIntegracaoTest.java`: 3 testes de integração cobrindo emissão e consulta filtrada de todos os tipos de evento de auditoria (sem dados sensíveis), exatidão do relatório por unidade/canal frente ao SQL e autorização (`401` sem token, `403` para outra unidade ou perfis não gerenciais).
- `etapa-02/{modelagem-fluxo-a,contratos-api}.md`, `backend/src/main/resources/static/openapi.yaml`, `backend/postman/raizes-fluxo-a.postman_collection.json`, `backend/README.md` e `etapa-09/validacao.md`: DER, governança LGPD, promoções conceituais, contratos, Swagger (22 rotas), coleção Postman (30 requisições) e validação.

## 4. Testes, comandos e evidências de correspondência com o banco

| Comando / verificação | Resultado |
|---|---|
| `mvnw.cmd -o -Dmaven.repo.local=C:\projeto-raizes\backend\.mvn\local-repository clean verify` | **BUILD SUCCESS**; 93 fontes Java de produção, 7 classes de teste; **28 testes**, 0 falhas, 0 erros; JAR empacotado. |
| Inicialização local do JAR + `GET /api/v1/health` | Flyway aplicou `V11__auditoria_unidade_e_indices.sql` (esquema v11); Hibernate `ddl-auto=validate` aprovou; `status=UP`, `database=UP`. |
| Conferência `GET /api/v1/auditoria` vs SQL em `raizes` | `GERENTE` da unidade 1 recebeu `totalItems = 31` (soma exata dos eventos com `unidade_id = 1` no SQL: `CRIACAO_PEDIDO` 8, `TRANSICAO_PEDIDO` 6, `RESULTADO_PAGAMENTO` 8, `ACUMULO_FIDELIDADE` 3, `RESGATE_FIDELIDADE` 2, `ESTORNO_RESGATE_FIDELIDADE` 1, `EXPIRACAO_RESERVA` 1, `ACESSO_DADO_PESSOAL` em `PEDIDO` 1, `LOGIN` da unidade 1); `ADMIN` recebeu `totalItems = 52` (31 da unidade 1 + 21 eventos globais com `unidade_id IS NULL`). |
| Conferência `GET /api/v1/pedidos/relatorios/unidades-canais` vs SQL em `raizes` | API e SQL (`GROUP BY unidade_id, canal_pedido`) retornaram exatamente `totalPedidos = 8`, `subtotalItensTotal = 478.80`, `descontoFidelidadeTotal = 10.00`, `valorTotal = 468.80` e `pontosResgatadosTotal = 200` para `(unidadeId = 1, canalPedido = APP)`. |
| Banco `raizes_test` após a suíte | `0` pedidos, `0` contas/movimentos de fidelidade e `0` eventos de negócio residuais; saldos de estoque restaurados. |

## 5. Pendências

- Nenhuma pendência bloqueante para a tarefa 10.
- Publicação do repositório Git público e formatação final do PDF ABNT (incluindo a declaração de uso de IA exigida pelo roteiro) pertencem à etapa final de consolidação e entrega.

