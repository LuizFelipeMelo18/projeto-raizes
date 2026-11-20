# Raízes do Nordeste — Back-end

Entrega técnica consolidada (etapa 11) para o Fluxo A especificado em `../etapa-02/`. Usa **Java 21**, **Spring Boot 4.1.2**, **Maven 3.9.16** pelo Wrapper, **PostgreSQL 19 local**, **Spring Data JPA (Hibernate)**, **Flyway 12.4.0**, **Spring Security** e **JJWT 0.12.6**. Não usa Docker nem Testcontainers.

## Evidências e artefatos obrigatórios (Seção 7 do roteiro)

| Entregável obrigatório do roteiro | Caminho no repositório / URL local |
|---|---|
| Código-fonte organizado em camadas (`api`, `application`, `domain`, `infrastructure`) | `backend/src/main/java/br/com/raizes/**` (93 fontes Java) e `backend/src/test/java/br/com/raizes/**` (9 classes, 35 testes) |
| Migrations versionadas Flyway (`V1` a `V11`) + seed local/test (`R__demo_seed.sql`) | `backend/src/main/resources/db/migration/` e `backend/src/main/resources/db/demo/R__demo_seed.sql` |
| Exemplo de variáveis de ambiente sem segredos | `backend/.env.example` |
| Swagger UI local (WebJar empacotado, sem CDN) | `http://localhost:8080/swagger-ui.html` (redireciona para `http://localhost:8080/swagger/index.html`) |
| Especificação OpenAPI 3.0.3 (22 paths, 25 operações) | `http://localhost:8080/openapi.yaml` (`backend/src/main/resources/static/openapi.yaml`) |
| Coleção Postman v2.1 em 7 pastas (38 requisições, cenários `T01`..`T18`) | `backend/postman/raizes-fluxo-a.postman_collection.json` |
| DER exportado (PNG e PDF) + Casos de Uso, Arquitetura/Classes e Sequência | `../etapa-11/diagramas/der-modelo-dados.pdf`, `der-modelo-dados.png`, `diagrama-casos-de-uso.png`, `diagrama-arquitetura-classes.png`, `diagrama-sequencia-fluxo-a.png` |
| Documento acadêmico único em padrão ABNT | `../RU_Projeto_Back_End.PDF` (cópia em `../etapa-11/RU_Projeto_Back_End.PDF`) — substitua `RU` pelo seu Registro Único antes de submeter no AVA |
| Repositório Git público (preencher após `git push -u origin main`) | `<INSERIR_URL_PUBLICA_DO_REPOSITORIO_GIT_ANTES_DO_ENVIO_NO_AVA>` |

## Estado atual

- Implementados: migrations do DER (`V1` a `V11`), seed local/test, unidades, produtos e cardápio paginados, preço e disponibilidade por unidade, saldo e movimentos de estoque, cadastro de CLIENTE, login com BCrypt, JWT HS256, papéis CLIENTE/TOTEM/ATENDENTE/COZINHA/GERENTE/ADMIN, criação e consulta de pedido, reserva atômica, gateway mock, aprovação/recusa/resultado indeterminado, reconciliação, cancelamento, expiração e estados até entrega, idempotência, acompanhamento anônimo, consentimento específico de fidelidade (registro e revogação), programa de fidelidade (saldo, histórico, acúmulo após `ENTREGUE`, resgate simples como desconto e devolução em recusa/cancelamento/expiração), auditoria completa com unidade e consulta paginada (`GET /api/v1/auditoria`), relatório consolidado de pedidos por unidade e canal (`GET /api/v1/pedidos/relatorios/unidades-canais`), erro JSON uniforme, health check e Swagger local.
- O Fluxo A está executável: Pedido → Pagamento mock → Atualização de status → Acúmulo de pontos na entrega, resgate em pedido posterior, trilha de auditoria e relatório por unidade/canal. A chamada ao gateway ocorre entre duas transações de banco. Resposta incerta retém a reserva até reconciliação; pedido sem tentativa expira após 30 minutos.
- O contrato planejado completo está em `../etapa-02/contratos-api.md`; `openapi.yaml` descreve somente rotas executáveis. ADMIN é global; equipe e TOTEM têm unidade obrigatória. Cadastro público cria exclusivamente CLIENTE.

## 1. Preparar PostgreSQL local

Instale uma instância local de PostgreSQL e confirme que `psql` está disponível. Nesta máquina, o ZIP de binários PostgreSQL 19 fornecido pelo solicitante foi extraído em `../.local/postgresql-19`, com cluster em `../.local/pgdata`. Para iniciar esse cluster em um terminal separado a partir da raiz do projeto (mantenha-o aberto):

```powershell
& '.\.local\postgresql-19\pgsql\bin\postgres.exe' -D '.\.local\pgdata' -h 127.0.0.1 -p 5432
```

Em outra máquina, a distribuição pode ser instalada normalmente. Para inicializar um ZIP portátil pela primeira vez, extraia-o para `../.local/postgresql-19`, execute `initdb.exe -D ..\.local\pgdata -U postgres -W -A scram-sha-256 -E UTF8` e então inicie `postgres.exe` como acima. `.local/` e o ZIP são ignorados pelo Git. Em um terminal com acesso administrativo ao PostgreSQL:

```powershell
psql -h localhost -U postgres -d postgres
```

No prompt do `psql`:

```sql
CREATE ROLE raizes_app LOGIN;
\password raizes_app
CREATE DATABASE raizes OWNER raizes_app;
CREATE DATABASE raizes_test OWNER raizes_app;
```

O comando `\password` solicita a senha interativamente, sem registrá-la no SQL. Nesta máquina, papel e bancos já foram criados; não repita os comandos `CREATE`. Em outra máquina, se já existirem, use os existentes e ajuste as variáveis. Não crie tabelas manualmente: Flyway aplica as migrations versionadas V1 a V11.

## 2. Configurar variáveis

No PowerShell, a partir da pasta `backend`:

```powershell
Copy-Item .env.example .env
```

Edite `.env`, substitua a senha do banco e gere uma chave JWT aleatória de 32 bytes em Base64 para `JWT_SECRET_BASE64` (por exemplo, no PowerShell: `$b = New-Object byte[] 32; $r = [Security.Cryptography.RandomNumberGenerator]::Create(); $r.GetBytes($b); [Convert]::ToBase64String($b)`). O arquivo `.env` é ignorado pelo Git. Spring Boot lê variáveis de ambiente, mas não carrega `.env` automaticamente; importe o arquivo no terminal atual:

```powershell
Get-Content .env | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
    $pair = $_ -split '=', 2
    Set-Item -Path "Env:$($pair[0])" -Value $pair[1]
}
```

O perfil `local` é o padrão. Variáveis: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_BASE64`, `SERVER_PORT`, `SPRING_PROFILES_ACTIVE`. A chave JWT é obrigatória e a aplicação rejeita valores inferiores a 32 bytes. Não inclua credenciais reais em commits ou capturas de tela.

## 3. Compilar e iniciar

```powershell
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```

O Wrapper executa Maven 3.9.16; usa uma distribuição já presente em `MAVEN_HOME` ou no cache do usuário e, se necessário, baixa a distribuição indicada em `.mvn/wrapper/maven-wrapper.properties` para uma pasta ignorada pelo Git. Em Linux/macOS, use `sh ./mvnw` (requer `curl` e `unzip` na primeira execução sem cache).

Se o ambiente tentar criar o cache Maven em um caminho sem permissão, acrescente `-Dmaven.repo.local=.mvn/local-repository` aos comandos. Nesta sessão, o build offline foi validado com uma cópia temporária do cache nessa pasta ignorada pelo Git. Como o cache não é versionado, outra máquina precisará de acesso ao Maven Central na primeira compilação.

O módulo de integração Flyway do Spring Boot está temporariamente fixado em 4.1.0 porque é o único artefato dessa linha presente no cache local; os demais módulos Spring Boot usam 4.1.2. A migração e a inicialização foram confirmadas com PostgreSQL 19; alinhe o módulo a 4.1.2 quando o Maven Central estiver acessível. Flyway 12.4.0 avisa que sua última versão verificada é PostgreSQL 18, embora tenha aplicado as migrations 1, 2 e 3 nesta instância 19.

Na inicialização, Flyway aplica as migrations versionadas `V1` a `V11` e, no perfil `local`, `R__demo_seed.sql`. V6 adiciona estados e expiração, V7 amplia o cenário mock, V8 guarda o histórico de respostas, V9 impede segunda baixa/liberação do mesmo produto no mesmo pedido, V10 estrutura a conta e o ledger de fidelidade (`fidelidade_conta`, `fidelidade_movimento` e colunas de fidelidade em `pedido`) e V11 adiciona `unidade_id` e índices de consulta à tabela `auditoria`. JPA usa `ddl-auto=validate`: **Flyway é a fonte de verdade do esquema**, e Hibernate só verifica as entidades mapeadas. Uma falha de conexão ou incompatibilidade de mapeamento impede a inicialização.

Verifique:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/health
Invoke-RestMethod 'http://localhost:8080/api/v1/unidades?page=1&limit=20'
Invoke-RestMethod 'http://localhost:8080/api/v1/unidades/1/cardapio?page=1&limit=20'
Invoke-RestMethod 'http://localhost:8080/api/v1/produtos?page=1&limit=20'
```

Health deve retornar `status=UP` e `database=UP`. A unidade 1 contém produtos 10, 11 e 12; a unidade 2 contém 10 e 11. Preço e saldo são específicos da unidade. Se `SERVER_PORT` for alterada, ajuste as URLs.

Swagger UI: `http://localhost:8080/swagger-ui.html` (redireciona para `/swagger/index.html`). Especificação: `http://localhost:8080/openapi.yaml`. Os arquivos JS/CSS da interface vêm de um WebJar empacotado, sem CDN.

## Catálogo e estoque

| Método e rota | Acesso | Resultado |
|---|---|---|
| `GET /api/v1/unidades` | Público | Unidades ativas, paginadas |
| `GET /api/v1/produtos` | Público | Produtos ativos, paginados; não há preço global |
| `GET /api/v1/unidades/{id}/cardapio` | Público | Preço `BigDecimal` e disponibilidade da unidade, paginados |
| `GET /api/v1/estoque?unidadeId=1&produtoId=10` | GERENTE da unidade ou ADMIN | Saldo físico, reservado e disponível, paginado |
| `GET /api/v1/estoque/movimentacoes?unidadeId=1` | GERENTE da unidade ou ADMIN | Histórico paginado, data e ID decrescentes |
| `POST /api/v1/estoque/movimentacoes` | GERENTE da unidade ou ADMIN | Entrada ou saída manual; retorna movimento e saldo |

Exemplo de movimento: `{"unidadeId":1,"produtoId":10,"tipo":"ENTRADA","quantidade":10,"motivo":"Reposição local"}`. A saída aceita somente o disponível (`fisico - reservado`); um excesso retorna 409. O endpoint não aceita `RESERVA`, `LIBERACAO` ou `BAIXA`: criação do pedido reserva, aprovação baixa, recusa/cancelamento/expiração sem tentativa liberam. Preços são `NUMERIC(12,2)` no PostgreSQL e `BigDecimal` na API. O cardápio busca os saldos da página em lote, sem consulta de saldo por produto.

## Identidade e autorização

| Método e rota | Acesso | Efeito |
|---|---|---|
| `POST /api/v1/clientes` | Público | Cria somente CLIENTE; senha recebe hash BCrypt |
| `POST /api/v1/auth/login` | Público | Retorna JWT assinado, expira em 3600 segundos |
| `GET /api/v1/usuarios/me` | JWT | Retorna dados próprios e audita o acesso |
| `PUT /api/v1/usuarios/me/consentimentos/fidelidade` | CLIENTE | Registra concessão ou revogação do consentimento de fidelidade |
| `GET /api/v1/fidelidade/consentimento` | CLIENTE | Consulta estado atual do consentimento de fidelidade |
| `PUT /api/v1/fidelidade/consentimento` | CLIENTE | Concede ou revoga consentimento de fidelidade |
| `GET /api/v1/fidelidade/saldo` | CLIENTE | Saldo, totais por tipo, conferência do ledger e regras vigentes |
| `GET /api/v1/fidelidade/movimentacoes` | CLIENTE | Histórico paginado de acúmulos, resgates e estornos |
| `POST /api/v1/pedidos` | CLIENTE, TOTEM, ATENDENTE conforme canal/unidade | Cria pedido, aplica eventual resgate de pontos e reserva estoque uma vez por chave UUID |
| `GET /api/v1/pedidos` | CLIENTE próprio, equipe da unidade ou ADMIN | Filtra por `canalPedido`, `status`, `unidadeId`; pagina |
| `GET /api/v1/pedidos/{id}` | CLIENTE dono, equipe da unidade ou ADMIN | Retorna pedido e audita o acesso; CLIENTE alheio recebe 404, equipe de outra unidade 403, TOTEM 403 |
| `GET /api/v1/pedidos/acompanhar/{codigo}` | Público com código aleatório de 144 bits | Retorna somente status e atualização do pedido anônimo |
| `POST /api/v1/pedidos/{id}/pagamentos` | CLIENTE dono, TOTEM criador ou ATENDENTE da unidade | Registra tentativa e chama gateway mock fora de transação; 200 resolvido, 202 indeterminado |
| `POST /api/v1/pedidos/{id}/pagamentos/reconciliacao` | GERENTE da unidade ou ADMIN | Consulta gateway e resolve estado incerto; reserva segue retida enquanto incerto |
| `PATCH /api/v1/pedidos/{id}/status` | COZINHA, ATENDENTE, GERENTE da unidade ou ADMIN | EM_PREPARO → PRONTO → ENTREGUE conforme papel; credita pontos ao entregar |
| `POST /api/v1/pedidos/{id}/cancelamento` | CLIENTE dono, TOTEM criador ou ATENDENTE da unidade | Cancela antes da tentativa, libera reserva e estorna pontos resgatados |
| `GET /api/v1/auditoria` | GERENTE da unidade ou ADMIN | Consulta paginada de eventos de auditoria (ator, recurso, ação, unidade, resultado, horário) |
| `GET /api/v1/pedidos/relatorios/unidades-canais` | GERENTE da unidade ou ADMIN | Relatório consolidado de pedidos, subtotais, descontos e totais por unidade e canal |

Na criação, envie `Idempotency-Key: <UUID>` e `{"unidadeId":1,"canalPedido":"APP","modalidadeAtendimento":"RETIRADA","formaPagamento":"MOCK","pontosResgate":0,"itens":[{"produtoId":10,"quantidade":2}]}`. `canalPedido` aceita `APP`, `TOTEM`, `BALCAO`, `PICKUP` e `WEB`; `modalidadeAtendimento` aceita `RETIRADA` e `CONSUMO_LOCAL` independentemente do canal. CLIENTE usa APP/WEB/PICKUP; TOTEM usa TOTEM; ATENDENTE usa BALCAO/PICKUP. TOTEM e ATENDENTE criam pedido sem `clienteId`; `criado_por_id` registra o dispositivo/operador da unidade. Preço, subtotal, desconto e total são calculados no servidor. Resposta inicial 201, repetição da mesma chave/conteúdo 200 com `Idempotency-Replayed: true`; conteúdo diferente 409. Pedido anônimo recebe `codigoAcompanhamento` somente na criação/repetição; a consulta pública desse código não expõe cliente, itens, total nem ID.

**Regras de fidelidade:**
- **Consentimento LGPD:** opt-in explícito e revogável para a finalidade `FIDELIDADE`. Pedidos anônimos ou criados sem consentimento ativo têm `participaFidelidade=false` e não acumulam nem resgatam pontos. Se o cliente revogar antes da entrega, o pedido não credita pontos ao chegar a `ENTREGUE`.
- **Acúmulo:** `1 ponto` por cada `R$ 1,00` inteiro efetivamente pago (`floor(total)`), creditado exclusivamente na transição para `ENTREGUE`. A constraint `UNIQUE(pedido_id, tipo)` em `fidelidade_movimento` e o bloqueio pessimista da conta impedem crédito duplicado em reprocessamento.
- **Resgate e limite:** `pontosResgate` opcional na criação do pedido, sempre em múltiplos de `100 pontos = R$ 5,00` de desconto, limitado a **50% do subtotal dos itens** (`total = subtotalItens - descontoFidelidade > 0`).
- **Devolução automática:** se um pedido com pontos resgatados tiver pagamento recusado (direto ou na reconciliação), for cancelado antes do pagamento ou expirar sem tentativa, um movimento `ESTORNO_RESGATE` devolve os pontos ao saldo uma única vez.
- **Autorização e consistência:** apenas `CLIENTE` acessa `/api/v1/fidelidade/**`, sempre restrito ao próprio `usuario_id` do JWT. O saldo em `fidelidade_conta` é igual a `SUM(ACUMULO) - SUM(RESGATE) + SUM(ESTORNO_RESGATE)` em `fidelidade_movimento`.

No pagamento, envie nova chave `Idempotency-Key: <UUID>` e `{"cenarioMock":"APROVAR"}`. `RECUSAR` devolve recusa; `FALHA_TECNICA` devolve resultado indeterminado. `INDETERMINADO_APROVAR` e `INDETERMINADO_RECUSAR` demonstram resolução posterior via reconciliação; `INDETERMINADO_PENDENTE` permanece incerto. Nenhum desses cenários cobra dinheiro. A tentativa `PENDENTE` é persistida antes da chamada ao `PaymentGateway`; `pagamento_evento` conserva as respostas de solicitação/reconciliação e seus payloads. Resultado incerto retorna 202 e mantém o estoque reservado. Só a confirmação de aprovação baixa, e só a confirmação de recusa libera. A tarefa agendada cancela e libera reservas vencidas **sem tentativa**; pode-se ajustar `raizes.pedidos.expiracao.intervalo-ms` (padrão 60000). Uma incerteza persistente exige verificação operacional do gateway; o sistema não a transforma em recusa por tempo decorrido.

**LGPD, auditoria e promoções conceituais:**
- **Inventário, finalidade e base legal (Lei 13.709/2018):** `nome` e `email` (identificação e autenticação para execução de contrato, art. 7º, V); `senha_hash` (BCrypt custo 12, nunca exposto, art. 7º, V e art. 46); `consentimento`, `fidelidade_conta` e `fidelidade_movimento` (finalidade exclusiva `FIDELIDADE`, mediante consentimento livre e revogável do titular, art. 7º, I e art. 8º, § 5º); `pedido`/`item_pedido` (execução contratual e obrigação fiscal/contábil, art. 7º, II e V, com suporte a compra anônima via `codigo_acompanhamento` sem dados pessoais); `auditoria` (rastreabilidade e segurança do tratamento, art. 7º, II, art. 37 e art. 46).
- **Auditoria sem payload sensível:** todas as ações críticas (`CADASTRO_CLIENTE`, `LOGIN`, `CONSENTIMENTO_FIDELIDADE`, `ACESSO_DADO_PESSOAL`, `CRIACAO_PEDIDO`, `RESULTADO_PAGAMENTO`, `TRANSICAO_PEDIDO`, `CANCELAMENTO_PEDIDO`, `EXPIRACAO_RESERVA`, `MOVIMENTO_ESTOQUE`, `RESGATE_FIDELIDADE`, `ESTORNO_RESGATE_FIDELIDADE`, `ACUMULO_FIDELIDADE`) gravam `ator_id`, `sujeito_usuario_id`, `pedido_id`, `unidade_id`, `recurso`, `acao`, `resultado`, `request_id` e `criado_em`, sem senha, hash, JWT ou payload financeiro.
- **Retenção e anonimização:** contas encerradas são anonimizadas in-place (`ativo = FALSE`, `nome = 'Titular Anonimizado'`, `email = 'anonimizado+<id>@anon.raizes.invalid'`), invalidando tokens ativos e preservando a integridade referencial contábil/fiscal dos pedidos e da trilha de auditoria (que referencia apenas IDs numéricos).
- **Promoções/campanhas (evolução conceitual `RF-08`):** detalhada na seção 9 de `../etapa-02/modelagem-fluxo-a.md` (`CAMPANHA_PROMOCIONAL` com vigência, escopo opcional por unidade e canal, e precedência determinística: `subtotalItens` → `descontoPromocional` → limite de 50% de `descontoFidelidade` sobre a base pós-promoção → `total` pago como base de acúmulo).

Use `Authorization: Bearer <accessToken>` nas rotas protegidas. O servidor confere assinatura e expiração e relê usuário ativo, perfil e unidade no banco em cada requisição. A equipe e o TOTEM não escolhem unidade pelo token; a associação vem do cadastro persistido. ADMIN é global e só existe na seed local/test nesta etapa. Respostas e auditoria não incluem senha, hash, JWT ou corpo de requisição. O registro de consentimento guarda finalidade, escolha, versão e instante e governa a participação do cliente na fidelidade.

## Perfil de teste

O perfil `test` aponta para outro banco PostgreSQL local (`raizes_test`) e aplica as mesmas migrations e seed. Não usa H2 nem Testcontainers. Configure `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` e `JWT_SECRET_BASE64` e execute:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'test'
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/raizes_test'
$env:TEST_DB_USERNAME = 'raizes_app'
$env:TEST_DB_PASSWORD = $env:DB_PASSWORD
.\mvnw.cmd clean verify
```

`PedidoAccessPolicyTest`, `RegraFidelidadeTest` e `EstoqueDomainTest` validam em memória as invariantes do domínio (escopo de acesso, máquina de estados, cálculo/limite de pontos e saldo físico/reservado). `AutorizacaoIntegracaoTest`, `EstoqueIntegracaoTest`, `PedidoIntegracaoTest`, `FluxoPedidoIntegracaoTest`, `FidelidadeIntegracaoTest` e `AuditoriaERelatorioIntegracaoTest` iniciam a API em porta aleatória contra o banco PostgreSQL dedicado `raizes_test`; verificam identidade, propriedade, escopo por unidade, catálogo, movimentos, disponibilidade, concorrência pelo último item disponível, reserva, idempotência, pagamento aprovado/recusado/indeterminado, reconciliação, estados, cancelamento, expiração, consentimento, acúmulo após entrega, resgate com limite, devolução de pontos, consulta de auditoria e relatório por unidade/canal. Seus pedidos, clientes e movimentos de teste são removidos ao fim.

## Plano de Testes e execução da coleção Postman (Seção 8 do roteiro)

A coleção oficial está em `postman/raizes-fluxo-a.postman_collection.json` (e o plano detalhado em `../etapa-10/plano-de-testes.md`), organizada em **7 pastas** e **38 requisições** executáveis em sequência:

1. **`Auth` (5 requisições):** realiza login das contas da seed (`cliente@raizes.invalid`, `cozinha@raizes.invalid`, `atendente@raizes.invalid`, `gerente@raizes.invalid`, `totem@raizes.invalid` com senha `Demo@1234`) e grava os JWTs nas variáveis da coleção (`clienteToken`, `cozinhaToken`, `atendenteToken`, `gerenteToken`, `totemToken`).
2. **`Produtos e Estoque` (3 requisições):** verifica `/api/v1/health`, consulta o cardápio da unidade 1 e registra entrada de estoque para garantir saldo repetível.
3. **`Pedidos` (8 requisições):** habilita consentimento, cria pedidos para aprovação, recusa, reconciliação, cancelamento e pedido anônimo no totem (`codigoAcompanhamento`).
4. **`Pagamentos` (7 requisições):** aprova pagamento mock (`EM_PREPARO`), avança `PRONTO` → `ENTREGUE`, recusa pagamento mock (`PAGAMENTO_RECUSADO`) e executa pagamento indeterminado (`202`) seguido de reconciliação (`200`).
5. **`Fidelidade` (6 requisições):** confere saldo acumulado após `ENTREGUE` (`119` pontos), cria pedido posterior com resgate de `100` pontos (`R$ 5,00` de desconto), estorna pontos após recusa, consulta histórico paginado e testa revogação/reativação de consentimento.
6. **`Auditoria e Relatórios` (3 requisições):** lista pedidos filtrando por `canalPedido=APP&status=ENTREGUE`, consulta a trilha de auditoria (`GET /api/v1/auditoria`) e o relatório consolidado por unidade e canal (`GET /api/v1/pedidos/relatorios/unidades-canais`).
7. **`Erros` (6 requisições):** valida o contrato padronizado de erro (`ApiError`) para `401 NAO_AUTENTICADO`, `403 ACESSO_NEGADO`, `422 CANAL_PEDIDO_INVALIDO`, `422 ITEM_INVALIDO`, `404 UNIDADE_NAO_ENCONTRADA` e `409 ESTOQUE_INSUFICIENTE`.

**Como executar:**
- Ambiente: PostgreSQL local ativo (`raizes` com `V1`..`V11` + `R__demo_seed.sql` aplicados automaticamente na subida) e API iniciada em `http://127.0.0.1:8080` (`baseUrl` padrão da coleção).
- Ordem: execute as pastas de `1` (`Auth`) a `7` (`Erros`) no Collection Runner do Postman (ou importe no Insomnia). Os scripts de pré-requisição geram `Idempotency-Key` UUID automaticamente e os scripts de teste encadeiam `pedidoId`, `codigoAnonimo` e tokens JWT sem configuração manual.

### Tabela consolidada de cenários (18 cenários: 11 positivos e 7 negativos — cobre toda a seção 8.3)

| ID | Tipo | Método e rota | Pré-condição | Entrada (headers / query / body) | Saída esperada (status + pontos do response) | Evidência (pasta / nome exato na coleção) |
|---|---|---|---|---|---|---|
| **T01** | Positivo (8.3.a.a) | `POST /api/v1/auth/login` | Seed `R__demo_seed.sql` aplicada (`cliente@raizes.invalid`) | `{"email":"cliente@raizes.invalid","senha":"Demo@1234"}` | `200 OK` + `accessToken`, `tokenType="Bearer"`, `user.perfil="CLIENTE"` | `Auth / T01 - Login cliente válido` |
| **T02** | Positivo | `GET /api/v1/unidades/1/cardapio?page=1&limit=20` | Unidade 1 ativa com 3 itens no cardápio | Query `page=1&limit=20` | `200 OK` + `totalItems=3`, `data[0].preco=39.90`, `disponivel=true` | `Produtos e Estoque / T02 - Consultar cardápio da unidade 1` |
| **T03** | Positivo (8.3.c.a) | `POST /api/v1/pedidos` | `clienteToken` válido, consentimento ativo, saldo disponível do produto 10 | Header `Idempotency-Key: <uuid>`; `{"unidadeId":1,"canalPedido":"APP","modalidadeAtendimento":"RETIRADA","formaPagamento":"MOCK","itens":[{"produtoId":10,"quantidade":3}]}` | `201 Created` + `status="AGUARDANDO_PAGAMENTO"`, `subtotalItens=119.70`, `total=119.70`, `participaFidelidade=true` | `Pedidos / T03 - Criar pedido válido no APP` |
| **T04** | Positivo (8.3.d.a) | `POST /api/v1/pedidos/{id}/pagamentos` | Pedido `pedidoAprovado` em `AGUARDANDO_PAGAMENTO` | Header `Idempotency-Key: <uuid>`; `{"cenarioMock":"APROVAR"}` | `200 OK` + `statusPagamento="APROVADO"`, `statusPedido="EM_PREPARO"`, `codigoMock="MOCK-OK"` | `Pagamentos / T04 - Aprovar pagamento mock` |
| **T05** | Positivo (8.3.d.a) | `PATCH /api/v1/pedidos/{id}/status` | Pedido em `EM_PREPARO`, `cozinhaToken` da unidade 1 | `{"status":"PRONTO"}` | `200 OK` + `statusAnterior="EM_PREPARO"`, `status="PRONTO"` | `Pagamentos / T05 - Marcar pedido PRONTO` |
| **T06** | Positivo (8.3.d.a) | `PATCH /api/v1/pedidos/{id}/status` | Pedido em `PRONTO`, `atendenteToken` da unidade 1, consentimento ativo | `{"status":"ENTREGUE"}` | `200 OK` + `statusAnterior="PRONTO"`, `status="ENTREGUE"` (credita 119 pontos) | `Pagamentos / T06 - Marcar pedido ENTREGUE e acumular pontos` |
| **T07** | Positivo | `GET /api/v1/fidelidade/saldo` | Pedido `ENTREGUE` com consentimento ativo (`clienteToken`) | Header `Authorization: Bearer {{clienteToken}}` | `200 OK` + `consentimentoAtivo=true`, `saldo>=119`, `saldoConsistente=true` | `Fidelidade / T07 - Consultar saldo de fidelidade após entrega` |
| **T08** | Positivo | `POST /api/v1/pedidos` | `clienteToken` com `saldo >= 100` e consentimento ativo | Header `Idempotency-Key: <uuid>`; `{"unidadeId":1,"canalPedido":"APP","modalidadeAtendimento":"RETIRADA","formaPagamento":"MOCK","pontosResgate":100,"itens":[{"produtoId":10,"quantidade":1}]}` | `201 Created` + `subtotalItens=39.90`, `pontosResgatados=100`, `descontoFidelidade=5.00`, `total=34.90` | `Fidelidade / T08 - Criar pedido com resgate de 100 pontos` |
| **T09** | Positivo | `GET /api/v1/pedidos?canalPedido=APP&status=ENTREGUE&page=1&limit=20` | Ao menos 1 pedido `APP` entregue do cliente | Query `canalPedido=APP&status=ENTREGUE&page=1&limit=20` | `200 OK` + `totalItems>=1`, todos os itens com `canalPedido="APP"` e `status="ENTREGUE"` | `Auditoria e Relatórios / T09 - Listar pedidos filtrando por canal e status` |
| **T10** | Negativo (8.3.a.b) | `GET /api/v1/pedidos/{id}` | Requisição sem cabeçalho `Authorization` | Sem token | `401 Unauthorized` + `error="NAO_AUTENTICADO"`, `requestId` presente | `Erros / T10 - Acesso sem token (401)` |
| **T11** | Negativo (8.3.a.c) | `GET /api/v1/auditoria` | Usuário autenticado com perfil `CLIENTE` (`clienteToken`) | Header `Authorization: Bearer {{clienteToken}}` | `403 Forbidden` + `error="ACESSO_NEGADO"`, `requestId` presente | `Erros / T11 - Perfil sem permissão (403)` |
| **T12** | Negativo (8.3.b.a) | `POST /api/v1/pedidos` | `clienteToken` válido, body sem o campo obrigatório `canalPedido` | Header `Idempotency-Key: <uuid>`; `{"unidadeId":1,"modalidadeAtendimento":"RETIRADA","formaPagamento":"MOCK","itens":[{"produtoId":10,"quantidade":1}]}` | `422 Unprocessable Entity` + `error="CANAL_PEDIDO_INVALIDO"` | `Erros / T12 - Campo obrigatório canalPedido ausente (422)` |
| **T13** | Negativo (8.3.b.b) | `POST /api/v1/pedidos` | `clienteToken` válido, item com quantidade negativa (`-2`) | Header `Idempotency-Key: <uuid>`; `{"unidadeId":1,"canalPedido":"APP","modalidadeAtendimento":"RETIRADA","formaPagamento":"MOCK","itens":[{"produtoId":10,"quantidade":-2}]}` | `422 Unprocessable Entity` + `error="ITEM_INVALIDO"` | `Erros / T13 - Formato de e-mail e quantidade inválidos (422)` |
| **T14** | Negativo (8.3.c.b) | `POST /api/v1/pedidos` | `clienteToken` válido, `unidadeId=999` inexistente | Header `Idempotency-Key: <uuid>`; `{"unidadeId":999,"canalPedido":"APP","modalidadeAtendimento":"RETIRADA","formaPagamento":"MOCK","itens":[{"produtoId":10,"quantidade":1}]}` | `404 Not Found` + `error="UNIDADE_NAO_ENCONTRADA"` | `Erros / T14 - Pedido em unidade inexistente (404)` |
| **T15** | Negativo (8.3.c.c) | `POST /api/v1/pedidos` | `clienteToken` válido, `quantidade=999` acima do saldo disponível | Header `Idempotency-Key: <uuid>`; `{"unidadeId":1,"canalPedido":"APP","modalidadeAtendimento":"RETIRADA","formaPagamento":"MOCK","itens":[{"produtoId":10,"quantidade":999}]}` | `409 Conflict` + `error="ESTOQUE_INSUFICIENTE"` | `Erros / T15 - Pedido com estoque insuficiente (409)` |
| **T16** | Negativo / Regra (8.3.d.b) | `POST /api/v1/pedidos/{id}/pagamentos` | Pedido `pedidoRecusado` em `AGUARDANDO_PAGAMENTO` | Header `Idempotency-Key: <uuid>`; `{"cenarioMock":"RECUSAR"}` | `200 OK` + `statusPagamento="RECUSADO"`, `statusPedido="PAGAMENTO_RECUSADO"`, `codigoMock="MOCK-DECLINED"`, reserva liberada | `Pagamentos / T16 - Pagamento mock recusado` |
| **T17** | Positivo (8.3.e.a) | `GET /api/v1/auditoria?unidadeId=1&recurso=PEDIDO&page=1&limit=20` | `gerenteToken` da unidade 1 após criação/transição/cancelamento de pedidos | Query `unidadeId=1&recurso=PEDIDO&page=1&limit=20` | `200 OK` + `totalItems>=1`, eventos `CRIACAO_PEDIDO` / `TRANSICAO_PEDIDO` / `CANCELAMENTO_PEDIDO` sem dados sensíveis | `Auditoria e Relatórios / T17 - Consultar trilha de auditoria da unidade` |
| **T18** | Positivo | `GET /api/v1/pedidos/relatorios/unidades-canais?unidadeId=1` | `gerenteToken` da unidade 1 com pedidos persistidos | Query `unidadeId=1` | `200 OK` + `unidadeFiltro=1`, `totalPedidos>=1`, somas `subtotalItensTotal`, `descontoFidelidadeTotal` e `valorTotal` consistentes | `Auditoria e Relatórios / T18 - Relatório de pedidos por unidade e canal` |

## Dados de demonstração

`R__demo_seed.sql` insere duas unidades, três produtos, cardápios e saldos. Insere contas **fictícias e locais** de todos os seis perfis, incluindo ADMIN, equipe da unidade 2 e segundo CLIENTE. Emails terminam em `@raizes.invalid`; a senha didática é `Demo@1234`, armazenada como hash BCrypt. Essas contas não devem ser usadas fora de local/test. A seed usa `ON CONFLICT DO NOTHING`, de modo que alterações locais nos registros existentes não são sobrescritas em uma reaplicação da migration repetível.

## Estrutura

| Pasta | Responsabilidade |
|---|---|
| `catalogo/api` | HTTP e contratos de consulta |
| `catalogo/application` | caso de uso de consulta e paginação |
| `catalogo/domain` | modelos de leitura sem dependências de JPA |
| `catalogo/infrastructure/jpa` | entidades e repositórios do catálogo |
| `estoque/{api,application,domain,infrastructure}` | saldos, movimentos e reserva/liberação/baixa transacionais |
| `identidade/{api,application,domain,infrastructure}` | cadastro, login, token e consentimento |
| `fidelidade/{api,application,domain,infrastructure}` | regras de taxa/limite, conta, ledger de movimentos (`ACUMULO`, `RESGATE`, `ESTORNO_RESGATE`), consulta de saldo e histórico por cliente |
| `pedidos/{api,application,domain,infrastructure}` | criação, listagem, consulta, estados, cancelamento, expiração, regra de dono/unidade, idempotência e persistência JPA |
| `pagamentos/{application,domain,infrastructure}` | porta PaymentGateway, adaptador mock, tentativa, reconciliação, resultado e histórico de payloads; rotas HTTP em `pedidos/api` |
| `auditoria/{api,application,infrastructure}` | registro e consulta paginada/autorizada de eventos sensíveis e acessos a dados pessoais |
| `shared/api` | paginação, requestId e erro JSON |
| `shared/infrastructure` | segurança HTTP |
| `saude` | verificação da aplicação e do banco |
| `db/migration` | esquema versionado obrigatório |
| `db/demo` | seed exclusiva dos perfis local/test |

O roteiro acadêmico exige declaração de uso de IA no documento final (ferramenta, prompts e trechos aproveitados). Essa declaração deve refletir o processo real antes da entrega.
