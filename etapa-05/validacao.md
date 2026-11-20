# Etapa 05 — catálogo e estoque por unidade

## Base validada antes de alterar código

Foram conferidos `etapa-04/validacao.md`, o contrato de estoque da etapa 02 e a matriz do roteiro Back-end. O PostgreSQL local respondeu em `127.0.0.1:5432`, `GET /api/v1/health` retornou `UP/UP`, login de GERENTE retornou unidade 1, e `GET /usuarios/me` confirmou o mesmo perfil e unidade. O comando `mvnw.cmd -o -Dmaven.repo.local=C:\projeto-raizes\backend\.mvn\local-repository test` passou com os cinco testes da etapa 04 (zero falhas). Não havia pendência bloqueante para a etapa 05; a diferença de patch do módulo Flyway permanece documentada, mas migrations e inicialização estavam funcionais.

## Arquivos criados ou alterados

- `backend/src/main/java/br/com/raizes/catalogo/{api,application,domain,infrastructure}/**`: listagem pública de produtos ativos, paginação e consulta de cardápio com preço por unidade. Os saldos dos produtos da página são carregados em lote, eliminando a consulta por produto.
- `backend/src/main/java/br/com/raizes/estoque/{api,application,domain,infrastructure}/**`: consulta paginada de saldos e histórico; entrada e saída manuais restritas a GERENTE da unidade ou ADMIN; bloqueio pessimista da linha de saldo; físico, reservado e disponível; movimentos e auditoria transacionais.
- `backend/src/main/java/br/com/raizes/shared/infrastructure/SecurityConfiguration.java`: autorização explícita das novas rotas, com negação das demais por padrão.
- `backend/src/main/resources/db/migration/V3__regras_e_indices_de_estoque.sql`: vínculo entre tipo de movimento e presença de pedido, índices para histórico paginado e produtos ativos. As restrições de `estoque_saldo` de V1 continuam a impedir físico/reservado negativos ou reserva maior que físico.
- `backend/src/main/resources/static/openapi.yaml` e `backend/README.md`: contratos, exemplos, configuração e limites da etapa.
- `backend/src/test/java/br/com/raizes/estoque/EstoqueIntegracaoTest.java`: testes HTTP/PostgreSQL e de reserva/liberação/baixa transacionais.

Nenhum segredo foi adicionado aos arquivos versionáveis; `.local/` e `backend/.env` continuam ignorados. Nenhum Docker ou Testcontainers foi introduzido.

## Regras verificadas

| Regra | Evidência |
|---|---|
| Unidade, produto e cardápio | `GET /unidades`, `/produtos` e `/unidades/{id}/cardapio` paginados; produto 10 custa 39,90 na unidade 1 e 42,00 na unidade 2. Produto global não expõe preço. |
| Disponibilidade local | Após saída do disponível do produto 10 na unidade 1, o cardápio da unidade 1 mostrou `disponivel=false`; a unidade 2 continuou `true` e seu saldo não mudou. |
| Escopo e perfil | GERENTE da unidade 1 recebeu 403 ao consultar ou movimentar a unidade 2; ATENDENTE recebeu 403; ausência de token recebeu 401. GERENTE da unidade 2 e ADMIN acessaram a unidade 2. |
| Físico/reservado | A saída manual preservou duas unidades reservadas: físico e reservado terminaram em 2, disponível em 0. Saída adicional recebeu 409 `ESTOQUE_INSUFICIENTE`, sem saldo negativo. |
| Reserva/liberação/baixa | Métodos internos usam `@Transactional`, `PESSIMISTIC_WRITE` e ordenação estável de produto. Reserva e liberação mantêm físico; baixa reduz físico e reservado uma vez por chamada. Pedido da unidade 1 não conseguiu reservar na unidade 2. Falha no segundo item reverteu a reserva do primeiro e não persistiu movimento parcial. |
| Paginação e N+1 | Produtos, cardápio, saldos e movimentos retornam envelope de página. Limite 1 foi testado em produtos, saldos e movimentos. Página de cardápio com três itens usou entre duas e quatro instruções SQL, sem uma consulta de saldo por item. |
| Auditoria | Movimento manual bem-sucedido gravou evento com o mesmo `requestId` da resposta, sem senha, token ou corpo de requisição. |

## Comandos e resultados

| Comando ou verificação | Resultado |
|---|---|
| `mvnw.cmd -o -Dmaven.repo.local=C:\projeto-raizes\backend\.mvn\local-repository clean verify` com variáveis de `backend/.env` | **BUILD SUCCESS**; 68 fontes compiladas, JAR gerado, **8 testes**, 0 falhas, 0 erros. |
| `mvnw.cmd ... test` após último ajuste de paginação | **BUILD SUCCESS**; 8 testes, 0 falhas, 0 erros. |
| Inicialização normal do JAR com perfil `local` | Flyway aplicou V3 em `raizes`, JPA validou o mapeamento, Tomcat iniciou na porta 8080. |
| `GET /api/v1/health` | `status=UP`, `database=UP`. |
| HTTP local | 3 produtos, cardápio da unidade 1 com 3 itens e da unidade 2 com 2; preços 39,90/42,00; saldo disponível do produto 10 na unidade 1 = 30; GERENTE da unidade 1 recebeu 403 ao consultar estoque da unidade 2. |
| Análise de OpenAPI com SnakeYAML | OpenAPI 3.0.3, 11 caminhos; produtos, estoque e movimentos presentes. |
| `flyway_schema_history` | V1, V2, seed repetível e V3 com sucesso em `raizes`; V3 também aplicada em `raizes_test`. |
| Limpeza dos testes | Zero pedidos e movimentos temporários no banco `raizes_test`; saldos de demonstração restaurados para 30/0, 50/0 e 25/0. |
| Busca de credenciais geradas em 104 arquivos versionáveis | Zero ocorrências. |

## Pendências para a etapa 06

- Integrar `ReservaEstoqueService` à criação, pagamento e cancelamento de pedidos. A idempotência e a máquina de estados devem ser impostas pelo caso de uso de pedido/pagamento; os métodos de estoque, isoladamente, são transacionais, mas não decidem se um pedido já foi processado.
- Implementar os contratos restantes do fluxo Pedido → Pagamento mock → Status, filtros por canal e coleção de testes de ponta a ponta. O histórico de movimentos por GET foi acrescentado nesta etapa como extensão operacional ao contrato da etapa 02.
- Alinhar `spring-boot-flyway` 4.1.0 ao Boot 4.1.2 quando Maven Central estiver acessível. Flyway 12.4.0 avisa que PostgreSQL 19 é mais novo que a última versão verificada por ele (18), embora as migrations e a inicialização tenham passado.

**Resultado de passagem:** catálogo, movimentos e disponibilidade por unidade estão comprovados em PostgreSQL local. Reserva, liberação e baixa estão prontas para uso transacional na etapa 06, com os limites de idempotência acima declarados.
