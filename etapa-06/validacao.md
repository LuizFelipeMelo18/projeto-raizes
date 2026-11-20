# Etapa 06 — criação e consulta de pedidos

## Base de trabalho

Foi lido o roteiro Back-end (PDF de 23 páginas, SHA-256 `DFEA9533ED57BF5BB67282800937B2C9C9A27AB6D34F82373C5D2DFF74588B17`) e conferido `etapa-05/validacao.md`. Antes das alterações, o PostgreSQL local e `GET /api/v1/health` responderam `UP`; os oito testes da etapa 05 passaram (zero falhas). Cardápio local, movimentos, bloqueio de saldo negativo e reserva/liberação/baixa transacionais estavam funcionais. Não havia pendência bloqueante. A integração da reserva ao caso de uso de pedido era o trabalho desta etapa.

## Arquivos criados ou alterados

- `backend/src/main/java/br/com/raizes/pedidos/api/PedidoCriacaoController.java`: POST, GET paginado e acompanhamento anônimo.
- `backend/src/main/java/br/com/raizes/pedidos/application/{CriarPedidoService,ListarPedidosService}.java`: validação, preços locais, autorização por origem, idempotência, reserva transacional, filtros e escopo.
- `backend/src/main/java/br/com/raizes/pedidos/infrastructure/IdempotenciaPedidoLock.java`: lock transacional PostgreSQL por chave UUID.
- `backend/src/main/java/br/com/raizes/pedidos/infrastructure/jpa/{PedidoEntity,ItemPedidoEntity,PedidoJpaRepository}.java`: escrita JPA, versão, chave/hash/código e buscas.
- `backend/src/main/java/br/com/raizes/catalogo/infrastructure/jpa/CardapioItemJpaRepository.java`: preço ativo dos itens selecionados em uma consulta.
- `backend/src/main/java/br/com/raizes/shared/infrastructure/SecurityConfiguration.java`: acesso explícito por rota e perfil.
- `backend/src/main/resources/db/migration/{V4__idempotencia_e_acompanhamento_pedido,V5__hash_pedido_varchar}.sql`: unicidade, restrições, índices e tipo do hash. V5 ajusta a coluna `CHAR(64)` de V4 para `VARCHAR(64)` sem reescrever uma migration já aplicada.
- `backend/src/test/java/br/com/raizes/pedidos/PedidoIntegracaoTest.java`: quatro testes HTTP/PostgreSQL.
- `backend/src/main/resources/static/openapi.yaml` e `backend/README.md`: contratos e operação local.
- `etapa-06/validacao.md`: este relatório.

## Regras e evidências

| Regra | Evidência |
|---|---|
| Persistência e preço congelado | POST local criou pedido **1** com `canalPedido=APP`, `total=79.80` e item 10 a `39.90`; GET após reiniciar a API manteve esses valores. Teste altera o preço no cardápio após criar e confirma que o item do pedido conserva `39.90`. |
| Reserva atômica | Para o pedido 1, o banco registrou um item e **um** movimento `RESERVA`; saldo da unidade 1: físico 30, reservado 2. Unidade 2: físico 25, reservado 0. Estoque insuficiente retorna 409 e não persiste pedido. |
| Idempotência | Mesma chave UUID e conteúdo retornou HTTP 200, mesmo ID, `Idempotency-Replayed: true` e nenhuma reserva adicional; criação original retornou 201. Conteúdo diferente com a mesma chave retornou 409 `IDEMPOTENCIA_CONFLITO`. Duas requisições concorrentes com a mesma chave produziram um pedido e uma reserva. |
| Canais e modalidade | `canalPedido` obrigatório com APP/TOTEM/BALCAO/PICKUP/WEB; modalidade RETIRADA/CONSUMO_LOCAL em campo separado. CLIENTE usa APP/WEB/PICKUP; TOTEM usa TOTEM; ATENDENTE usa BALCAO/PICKUP. Equipe e dispositivo só criam na unidade do token. |
| Anônimo e privacidade | TOTEM/ATENDENTE não informam cliente; `criado_por_id` registra dispositivo/operador. Criação anônima gera código aleatório de 144 bits; consulta pública por código retorna somente status e horário. Código não consta da listagem nem do detalhe protegido. |
| Consulta e escopo | Listagem paginada filtra por canal/status, com unidade opcional para equipe/ADMIN. CLIENTE vê somente pedidos próprios; outro cliente recebe 404 no detalhe. Equipe de outra unidade recebe 403 no detalhe/listagem. TOTEM não lista nem consulta detalhe protegido. |
| Erros | 401 sem token; 403 por perfil, canal ou unidade; 404 por pedido oculto/código inexistente; 409 por saldo/idempotência; 422 por canal, status ou chave inválidos. Todos usam o JSON de erro comum. |

## Comandos e resultados

| Verificação | Resultado |
|---|---|
| `mvnw.cmd -o -Dmaven.repo.local=C:\projeto-raizes\backend\.mvn\local-repository test` antes das alterações | 8 testes, 0 falhas/erros. |
| `mvnw.cmd -o -Dmaven.repo.local=C:\projeto-raizes\backend\.mvn\local-repository clean verify` após alterações | **BUILD SUCCESS**; 72 fontes Java e 4 arquivos de teste compilados; **12 testes**, 0 falhas/erros; JAR empacotado. |
| Inicialização do JAR com perfil `local` | Flyway aplicou V4/V5 em `raizes`; na inicialização final validou 7 migrations e esquema na versão 5, JPA validou, Tomcat iniciou em 8080. |
| `GET /api/v1/health` final | `status=UP`, `database=UP`. |
| Login, POST/replay, GET e consulta SQL local | 201/200 para pedido 1; lista filtrada contém o ID; SQL mostra um item e uma reserva, unidade 2 inalterada. |
| Parse do Swagger/OpenAPI com SnakeYAML 2.6 | OpenAPI 3.0.3, 13 caminhos, `GET` e `POST /api/v1/pedidos` presentes. |
| Segredos e ambiente | `.env`, logs, JAR, repositório Maven e `.local/` são ignorados; nenhum segredo real foi colocado em arquivo versionável. Sem Docker/Testcontainers. |

## Pendências para a etapa 07

- Implementar tentativa de pagamento mock e transições de status; ligar liberação ou baixa da reserva às decisões de pagamento/cancelamento. A máquina de estados ainda não é executável.
- Criar a coleção Postman/Insomnia do fluxo completo quando pagamento e status estiverem disponíveis.
- Alinhar `spring-boot-flyway` 4.1.0 a Spring Boot 4.1.2 quando o artefato estiver disponível no cache/rede. Flyway 12.4 avisa que PostgreSQL 19 é mais novo que a última versão verificada (18), mas migrations, testes e início passaram.

**Resultado de passagem:** criação e consulta de pedidos, preço copiado, reserva única, canal obrigatório/filtrável, modalidade separada, autorização e idempotência estão comprovados. A API local ficou iniciada em `127.0.0.1:8080` com pedido de demonstração 1 para a próxima etapa.
