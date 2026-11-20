# Etapa 04 — identidade, autorização e privacidade

## Pré-condição da etapa 03

Antes de alterar a API, foi lido `etapa-03/validacao.md` e conferida a matriz e os contratos da etapa 02. A compilação limpa passou. O ZIP PostgreSQL 19 fornecido pelo solicitante foi extraído para `.local/postgresql-19`, e um cluster local com autenticação SCRAM iniciou em `127.0.0.1:5432`. As credenciais geradas ficam em `.local/` e `backend/.env`, ignorados pelo Git.

Com a configuração normal, Flyway aplicou `V1__schema.sql` e `R__demo_seed.sql`, JPA iniciou com `ddl-auto=validate`, Tomcat abriu a porta 8080, `GET /api/v1/health` retornou `UP/UP`, e o catálogo retornou duas unidades e três itens na unidade 1. A etapa 03 passou a ter prova de compilação, migração e inicialização. O módulo `spring-boot-flyway` 4.1.0 continua em versão de patch distinta do Boot 4.1.2 por causa do cache offline; isso não bloqueou a execução.

## Implementação

- `backend/pom.xml`: JJWT 0.12.6, Jackson 2 isolado para JJWT, e dependência de teste do Spring Security.
- `backend/src/main/resources/db/migration/V2__identidade_e_privacidade.sql`: ADMIN global com unidade nula; demais perfis de equipe/TOTEM com unidade obrigatória; histórico de consentimento de fidelidade; referência ao titular e recurso na auditoria.
- `backend/src/main/resources/db/demo/R__demo_seed.sql`: ADMIN, equipe da unidade 2 e segundo CLIENTE fictícios, além dos perfis já existentes.
- `backend/src/main/java/br/com/raizes/identidade/**`: cadastro público sempre como CLIENTE, login BCrypt, JWT HS256 com expiração de uma hora, revalidação de usuário ativo/perfil/unidade a cada requisição, perfil próprio e eventos de consentimento.
- `backend/src/main/java/br/com/raizes/pedidos/**`: consulta por ID e política de acesso por dono ou unidade. Cliente alheio recebe 404; equipe de outra unidade recebe 403; ADMIN lê todas as unidades; TOTEM não usa a consulta compartilhada. A política também prepara pagamento por TOTEM criador na própria unidade.
- `backend/src/main/java/br/com/raizes/auditoria/**`: eventos mínimos para cadastro, login, consentimento e acesso a perfil/pedido. Não grava senha, hash, token nem corpo de requisição.
- `backend/src/main/java/br/com/raizes/shared/**`: 401/403/409/422 no erro JSON uniforme; rotas explicitamente autorizadas, demais rotas negadas por padrão.
- `backend/src/main/resources/static/openapi.yaml`, `backend/README.md`, `.env.example`: rotas reais, configuração de JWT/PostgreSQL portátil e execução dos testes.
- `backend/src/test/java/br/com/raizes/**`: testes de integração HTTP/PostgreSQL e regra de acesso ao pedido.

`backend/.env`, `.local/` e o ZIP fornecido são locais e ignorados; não integram o código-fonte versionável. Nenhum arquivo da etapa 02 foi alterado. A adição de ADMIN amplia o modelo daquela etapa de forma explícita: ADMIN é global, enquanto ATENDENTE, COZINHA, GERENTE e TOTEM mantêm vínculo obrigatório a uma unidade. A criação pública de conta não aceita escolha efetiva de papel ou unidade.

## Verificações

| Verificação | Resultado |
|---|---|
| `postgres.exe --version` | PostgreSQL 19.0. |
| `pg_isready -h 127.0.0.1 -p 5432` | Aceitando conexões. |
| Inicialização normal do JAR com `.env` local | Flyway aplicou V2 e reaplicou a seed; JPA validou mapeamentos; aplicação iniciou na porta 8080. Reinício posterior validou quatro registros de migration e informou esquema atualizado. |
| `flyway_schema_history` em `raizes_test` | V1, V2 e seed repetível aplicadas com sucesso. |
| `GET /api/v1/health` | `status=UP`, `database=UP`. |
| Login real e `GET /usuarios/me` | CLIENTE e ADMIN autenticaram; token não foi impresso nas evidências; perfil próprio retornou 200. |
| `mvnw.cmd -o -Dmaven.repo.local=C:\projeto-raizes\backend\.mvn\local-repository clean verify` com variáveis de `.env` | **BUILD SUCCESS**, 59 fontes Java compiladas, JAR gerado; **5 testes, 0 falhas, 0 erros**. |
| Testes HTTP/PostgreSQL | Cadastro sempre CLIENTE mesmo com campos de papel/unidade enviados, hash BCrypt, consentimento e revogação, login, 401 sem token/senha errada/token alterado, 422 de validação, 403 de papel/unidade, 404 para outro dono, acesso permitido à equipe da unidade e ADMIN, revalidação após desativação/mudança de perfil/unidade, auditoria de acesso. |
| Política de TOTEM | Teste aceita pagamento do pedido que criou na própria unidade; rejeita outra unidade ou criador. A rota de pagamento ainda não existe. |
| Limpeza de fixtures | `raizes_test` terminou com zero pedidos e zero clientes temporários de teste. |
| OpenAPI | YAML 3.0.3 analisado com SnakeYAML; 8 paths; `/openapi.yaml` respondeu 200. |

Os erros 401/403/404/422 testados apresentam `error`, `message`, `details`, `timestamp`, `path` e `requestId`; o cabeçalho `X-Request-Id` coincide com o corpo. A resposta de cadastro não contém senha ou hash. O `senha_hash` no banco difere da senha e é aceito pelo BCrypt.

## Limites e passagem para a etapa 05

- O fluxo Pedido → Pagamento mock → Atualização de status ainda não foi implementado. A consulta de pedido e a política de posse/unidade estão prontas; as rotas operacionais da etapa 05 devem reutilizar essa política e acrescentar testes de transações/estoque/idempotência.
- Fidelidade ainda não acumula pontos. O histórico de consentimento prepara a decisão futura sem presumir consentimento quando o campo é omitido.
- Os eventos de auditoria desta etapa cobrem acessos **bem-sucedidos** a dados pessoais; uma política completa de retenção/anonimização e auditoria de negativas pertence à entrega posterior.
- Flyway 12.4.0 avisa que PostgreSQL 19 é mais novo que sua última versão verificada (18). Migração e JPA passaram nesta instância. Alinhar `spring-boot-flyway` a 4.1.2 quando Maven Central estiver acessível.

**Resultado de passagem:** autenticação, 401, 403, propriedade e escopo por unidade estão comprovados por testes contra PostgreSQL local. A base de identidade está validada para a etapa 05, com as limitações acima declaradas.
