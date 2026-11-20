# Etapa 03 — verificação da base Spring Boot

## Base lida antes de alterar código

Foram conferidos `etapa-01/matriz-rastreabilidade.md`, `etapa-02/modelagem-fluxo-a.md`, `etapa-02/contratos-api.md` e `etapa-02/validacao.md`. A matriz tinha 54 IDs únicos e destino para as nove seções obrigatórias. A fonte `roteiro da atividade.pdf` foi reconferida: 23 páginas, SHA-256 `DFEA9533ED57BF5BB67282800937B2C9C9A27AB6D34F82373C5D2DFF74588B17`.

As decisões de domínio da etapa 02 não precisaram ser alteradas. A aplicação desta etapa implementa a infraestrutura e o catálogo de leitura, preparando as tabelas do Fluxo A; ainda não implementa pedido, pagamento ou transição operacional.

## Arquivos criados ou alterados

- `backend/pom.xml`, `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/*`: projeto Maven e Wrapper 3.9.16.
- `backend/.env.example`, `backend/.gitignore`, `backend/src/main/resources/application*.yml`, `backend/README.md`: perfis, variáveis e execução local/test.
- `backend/src/main/resources/db/migration/V1__schema.sql` e `db/demo/R__demo_seed.sql`: esquema Flyway e dados fictícios reproduzíveis.
- `backend/src/main/java/br/com/raizes/**`: inicialização; catálogo em API/Application/Domain/Infrastructure; saldo JPA; enums do fluxo; erro padrão, requestId, segurança HTTP e health check.
- `backend/src/main/resources/static/openapi.yaml`, `swagger-ui.html` e `swagger/index.html`: OpenAPI e interface Swagger local para os três endpoints efetivamente implementados.
- Este relatório.

Nenhum arquivo das etapas 01 e 02 foi alterado. A cópia de dependências em `backend/.mvn/local-repository` e `backend/target` são caches/artefatos ignorados pelo Git; não integram o código-fonte.

## Verificações e resultados

| Comando ou verificação | Resultado |
|---|---|
| Java `-version` | Java 21.0.12.1 disponível. |
| `mvnw.cmd -o -Dmaven.repo.local=C:\projeto-raizes\backend\.mvn\local-repository clean verify` | **BUILD SUCCESS**; 32 fontes Java compiladas e JAR executável gerado. Surefire informou `No tests to run`, pois ainda não há suíte. |
| Início normal com perfil `local` e senha fictícia de teste | Chegou a `flywayInitializer`, que tentou conectar ao PostgreSQL. **Falhou**: conexão recusada em `localhost:5432`; nenhuma migration foi executada. |
| Verificação local de PostgreSQL | `psql`/`pg_ctl` não encontrados, nenhum serviço PostgreSQL localizado, porta 5432 fechada. |
| Início de fumaça com Flyway e `ddl-auto` temporariamente desativados apenas na linha de comando | Aplicação iniciou em 8080. Isto verifica empacotamento/contexto/HTTP, **não** substitui a validação com banco. Processo encerrado ao final. |
| HTTP no início de fumaça | Swagger HTML 200, WebJar JS 200, `openapi.yaml` 200; erro de paginação 422, ID ilegível 400, rota pública inexistente 404 e rota protegida sem token 401, todos com esquema `error/message/details/timestamp/path/requestId`. `X-Request-Id` coincide com o corpo. Health retornou 503 padronizado sem banco em aproximadamente 6 segundos. |
| Parsing de `openapi.yaml` com SnakeYAML | OpenAPI 3.0.3, três paths válidos no YAML. |
| Inspeção do JAR | Inclui `V1__schema.sql`, `R__demo_seed.sql`, `openapi.yaml` e módulo de integração Flyway. |
| DER versus migration | As 10 entidades do DER têm tabelas correspondentes. Valores de seis enums Java conferem com os `CHECK` do SQL; nenhuma divergência encontrada. |
| Seed | Duas unidades, três produtos, cinco combinações de cardápio/estoque e cinco usuários fictícios com hash BCrypt; não contém senha em claro. |
| Docker/Testcontainers | Nenhum arquivo ou dependência correspondente. |

## Pendências que impedem a prova final solicitada

1. Disponibilizar PostgreSQL **local** e configurar `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Em seguida, executar o Wrapper, observar `V1__schema.sql` e `R__demo_seed.sql` aplicadas, `ddl-auto=validate` sem erro e `GET /api/v1/health` com 200/UP. Sem o serviço, **não há prova de migração bem-sucedida nem de inicialização normal**.
2. O módulo `spring-boot-flyway` foi fixado em 4.1.0 para uso do cache offline, enquanto o restante do Boot está em 4.1.2. O auto bootstrap foi observado, mas alinhar o módulo a 4.1.2 quando o Maven Central estiver acessível e repetir os testes com PostgreSQL.
3. Etapa 04: implementar login/JWT, papéis, pedidos, reserva/baixa, pagamento mock, transições, auditoria e os respectivos testes/coleção; atualizar OpenAPI para refletir somente os endpoints reais.

**Estado de passagem:** a compilação limpa está comprovada e o smoke test HTTP passou. A migração e o início normal permanecem bloqueados pela ausência do PostgreSQL local; este relatório não os declara validados.

## Revalidação em 20/11/2026 para a tarefa 04

O solicitante forneceu `postgresql-19.0-1-windows-x64-binaries.zip`. Os binários foram extraídos para `.local/postgresql-19`, o cluster foi criado em `.local/pgdata` com autenticação SCRAM, e o servidor iniciou diretamente com `postgres.exe` em `127.0.0.1:5432`. Credenciais geradas ficam apenas em `.local/` e `backend/.env`, ambos ignorados pelo Git.

Com o banco `raizes` pertencente a `raizes_app`, a inicialização **normal** do JAR aplicou `V1__schema.sql` e `R__demo_seed.sql` com sucesso. O JPA iniciou com `ddl-auto=validate`; Tomcat iniciou na porta 8080. `GET /api/v1/health` retornou `status=UP, database=UP`; `GET /api/v1/unidades` retornou duas unidades e o cardápio da unidade 1 retornou três produtos. `flyway_schema_history` registra a versão 1 aplicada, e a seed inseriu cinco usuários. Assim, a pendência 1 acima está resolvida para esta máquina.

O `pg_ctl` não conseguiu criar o processo no token restrito deste ambiente; executar `postgres.exe` diretamente funcionou. Flyway 12.4.0 avisou que PostgreSQL 19 é mais novo que a última versão verificada por ele (18), mas migração e validação JPA concluíram. O alinhamento de `spring-boot-flyway` 4.1.0 para 4.1.2 permanece uma melhoria de versão, sem bloquear esta base validada.
