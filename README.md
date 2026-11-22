# Raízes do Nordeste — API Back-end

API REST para uma rede de lanchonetes, implementada em Java 21 e Spring Boot 4.1.2. O fluxo principal cria um pedido multicanal, reserva o estoque da unidade, registra um pagamento simulado e acompanha o pedido até a entrega. O projeto também inclui autenticação JWT, perfis de acesso, fidelidade mediante consentimento, auditoria e relatórios.

## Entregáveis no repositório

| Item | Arquivo |
|---|---|
| Código-fonte, migrations Flyway e testes JUnit | [`backend/`](backend/) |
| Contrato OpenAPI 3.0.3 | [`backend/src/main/resources/static/openapi.yaml`](backend/src/main/resources/static/openapi.yaml) |
| Coleção Postman do fluxo e dos erros | [`backend/postman/raizes-fluxo-a.postman_collection.json`](backend/postman/raizes-fluxo-a.postman_collection.json) |
| Plano com 18 cenários de teste | [`documentacao/plano-de-testes.md`](documentacao/plano-de-testes.md) |
| Modelagem, regras de negócio e LGPD | [`documentacao/modelagem.md`](documentacao/modelagem.md) |
| DER, casos de uso, arquitetura, classes e sequência | [`documentacao/diagramas/`](documentacao/diagramas/) |

## Requisitos

- Java 21 e PostgreSQL 19 ou versão compatível, ativo na porta `5432`.
- PowerShell para os comandos abaixo. O Maven Wrapper acompanha o projeto e baixa Maven 3.9.16 e dependências na primeira execução, se necessário.
- Postman para executar a coleção; também é possível usar Newman. A documentação Swagger é servida pela própria API.

## Preparar o banco de dados

Abra o `psql` com um usuário administrador:

```powershell
psql -h localhost -U postgres -d postgres
```

No prompt do `psql`, crie o papel e os bancos. O banco `raizes_test` é usado exclusivamente pelos testes de integração.

```sql
CREATE ROLE raizes_app LOGIN;
\password raizes_app
CREATE DATABASE raizes OWNER raizes_app;
CREATE DATABASE raizes_test OWNER raizes_app;
```

Não crie tabelas manualmente. O Flyway aplica as migrations `V1` a `V12` na inicialização e carrega os dados de demonstração nos perfis `local` e `test`.

## Configurar o ambiente

Na pasta `backend`, copie o [exemplo de variáveis](backend/.env.example):

```powershell
Set-Location backend
Copy-Item .env.example .env
```

Edite `.env`: informe a senha de `raizes_app` em `DB_PASSWORD` e `TEST_DB_PASSWORD` e gere uma chave aleatória de pelo menos 32 bytes em Base64 para `JWT_SECRET_BASE64`. O Spring Boot não lê `.env` automaticamente; importe as variáveis no terminal em que executará o projeto:

```powershell
Get-Content .env | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
    $pair = $_ -split '=', 2
    Set-Item -Path "Env:$($pair[0])" -Value $pair[1]
}
```

`.env` é ignorado pelo Git. Os valores de `DB_URL`, `TEST_DB_URL`, usuários e porta podem ser alterados conforme a instalação local.

## Compilar e iniciar

Ainda em `backend`, execute:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

`verify` executa 35 testes, incluindo testes de integração no banco `raizes_test`, e gera o JAR em `backend/target`. A API usa o banco `raizes`. Se o Maven não puder gravar no cache padrão, defina antes de executar o Wrapper:

```powershell
$env:MAVEN_OPTS = '-Dmaven.repo.local=' + (Join-Path (Get-Location) '.mvn\local-repository')
```

Com a API iniciada, verifique [a saúde da aplicação](http://localhost:8080/api/v1/health). O retorno deve indicar `status=UP` e `database=UP`.

## Documentação e links

- **Repositório Git público:** [LuizFelipeMelo18/projeto-raizes](https://github.com/LuizFelipeMelo18/projeto-raizes).
- **Swagger público:** [luizfelipemelo18.github.io/projeto-raizes](https://luizfelipemelo18.github.io/projeto-raizes/) — disponível após ativar GitHub Pages na branch `main`, pasta `/(root)`.
- **Swagger local:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html). Inicie a API antes de abrir o link.
- **OpenAPI local:** [http://localhost:8080/openapi.yaml](http://localhost:8080/openapi.yaml); o arquivo também está [neste repositório](backend/src/main/resources/static/openapi.yaml).
- **Coleção Postman:** [arquivo JSON neste repositório](backend/postman/raizes-fluxo-a.postman_collection.json).
- **DER:** [PDF](documentacao/diagramas/der-modelo-dados.pdf) e [PNG](documentacao/diagramas/der-modelo-dados.png).

`localhost` identifica a máquina que está executando a API. O GitHub Pages publica somente a documentação; a API permanece local.

## Executar os testes

Os testes JUnit rodam com `mvnw.cmd clean verify` conforme a seção anterior. Para a coleção Postman, mantenha PostgreSQL e API ativos, importe o [JSON](backend/postman/raizes-fluxo-a.postman_collection.json) e use o `baseUrl` padrão `http://127.0.0.1:8080`. Execute a coleção inteira no Collection Runner, na ordem das sete pastas: `Auth`, `Produtos e Estoque`, `Pedidos`, `Pagamentos`, `Fidelidade`, `Auditoria e Relatórios`, `Erros`.

A seed cria contas fictícias locais, incluindo `cliente@raizes.invalid`, `cozinha@raizes.invalid`, `atendente@raizes.invalid`, `gerente@raizes.invalid` e `totem@raizes.invalid`, todas com a senha de demonstração `Demo@1234`. A pasta `Auth` obtém os tokens JWT e os salva nas variáveis da coleção. Os scripts da coleção também geram chaves de idempotência e repassam os IDs dos pedidos entre as requisições. O [plano de testes](documentacao/plano-de-testes.md) registra entrada, resultado esperado e nome da evidência para cada cenário `T01` a `T18`.
