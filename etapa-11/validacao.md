# Etapa 11 — Consolidação Final, Diagramas Exportados, PDF ABNT e Conferência da Matriz

## 1. Validação prévia da tarefa 10

Antes de iniciar a consolidação da entrega, foi conferido `etapa-10/validacao.md` junto à leitura integral de `C:\Users\felip\Downloads\roteiro da atividade.pdf` e de `etapa-01/matriz-rastreabilidade.md`:

- Os **35 testes JUnit** (`7` unitários de domínio + `28` de integração contra o PostgreSQL local dedicado `raizes_test`) e as **38 requisições da coleção Postman** (`backend/postman/raizes-fluxo-a.postman_collection.json`, organizadas em 7 pastas e cobrindo `T01`..`T18`) estavam aprovados, sem pendência técnica bloqueante.

## 2. Arquivos criados ou alterados na etapa 11

- `etapa-11/diagramas/der-modelo-dados.pdf` e `etapa-11/diagramas/der-modelo-dados.png`: DER completo exportado em PDF e PNG de alta resolução com as 13 tabelas do PostgreSQL 19 (`UNIDADE`, `PRODUTO`, `CARDAPIO_ITEM`, `ESTOQUE_SALDO`, `MOVIMENTO_ESTOQUE`, `USUARIO`, `PEDIDO`, `ITEM_PEDIDO`, `PAGAMENTO`, `PAGAMENTO_EVENTO`, `CONSENTIMENTO`, `FIDELIDADE_CONTA`, `FIDELIDADE_MOVIMENTO`, `AUDITORIA`), PKs, FKs, UKs e restrições `CHECK`.
- `etapa-11/diagramas/diagrama-casos-de-uso.png`: diagrama de casos de uso exportado com todos os atores obrigatórios (`Cliente`, `Totem`, `Atendente`, `Cozinha`, `Gerente/Admin` e `Gateway Externo de Pagamento Mock`).
- `etapa-11/diagramas/diagrama-arquitetura-classes.png`: diagrama de arquitetura em 4 camadas (`API`, `Application`, `Domain`, `Infrastructure`) e classes de domínio.
- `etapa-11/diagramas/diagrama-sequencia-fluxo-a.png`: diagrama de sequência do Fluxo Crítico A detalhando a chamada ao `PaymentGateway` fora de transação e o acúmulo/resgate de fidelidade.
- `RU_Projeto_Back_End.PDF` (e cópia em `etapa-11/RU_Projeto_Back_End.PDF`): documento acadêmico único (15 páginas A4, margens ABNT NBR 14724 3cm/2cm) com Capa, Folha de Rosto, Sumário paginado, 1. Introdução e Objetivos (implementado vs. proposto), 2. Análise e Requisitos (RF, RNF e multicanalidade `canalPedido`), 3. Diagramas e Modelagem (Casos de Uso, Feature Crítica, DER, Arquitetura/Classes e Sequência incorporados), 4. API REST e Contratos (25 operações em 22 paths + `ApiError`), 5. LGPD, Privacidade, Segurança e Auditoria, 6. Entrega Técnica e Guia de Execução Reprodutível, 7. Plano de Testes (`T01`..`T18`), 8. Conclusão e Declaração Obrigatória de Uso de IA e 9. Referências.
- `README.md` (raiz), `backend/README.md` e `etapa-01/matriz-rastreabilidade.md`: guia rápido na raiz, tabela de evidências e URLs locais no `backend/README.md` e fechamento da matriz de rastreabilidade.
- `.git` (repositório Git local inicializado em `C:\projeto-raizes`): estruturado com `.gitignore` protegendo `.local/`, `backend/.env`, `backend/target/`, `backend/.mvn/local-repository/` e `postgresql-19.0-1-windows-x64-binaries.zip`, e histórico de **7 commits** evolutivos representando a progressão real do projeto.

## 3. Conferência entre endpoints implementados e `openapi.yaml`

A conferência automatizada dos `@RestController` em `backend/src/main/java/br/com/raizes/**/api/*Controller.java` contra `backend/src/main/resources/static/openapi.yaml` (versão `0.7.0`) confirmou correspondência 1:1 de todos os **22 paths** e **25 operações HTTP** de negócio (`GET /api/v1/health`, `GET /api/v1/unidades`, `GET /api/v1/unidades/{id}/cardapio`, `GET /api/v1/produtos`, `GET /api/v1/estoque`, `GET/POST /api/v1/estoque/movimentacoes`, `POST /api/v1/clientes`, `POST /api/v1/auth/login`, `GET /api/v1/usuarios/me`, `PUT /api/v1/usuarios/me/consentimentos/fidelidade`, `GET/PUT /api/v1/fidelidade/consentimento`, `GET /api/v1/fidelidade/saldo`, `GET /api/v1/fidelidade/movimentacoes`, `POST/GET /api/v1/pedidos`, `GET /api/v1/pedidos/{id}`, `GET /api/v1/pedidos/acompanhar/{codigo}`, `POST /api/v1/pedidos/{id}/pagamentos`, `POST /api/v1/pedidos/{id}/pagamentos/reconciliacao`, `PATCH /api/v1/pedidos/{id}/status`, `POST /api/v1/pedidos/{id}/cancelamento`, `GET /api/v1/auditoria` e `GET /api/v1/pedidos/relatorios/unidades-canais`).

## 4. Conferência item a item da Matriz de Rastreabilidade Obrigatória (55 itens)

| ID | Requisito da matriz (`etapa-01/matriz-rastreabilidade.md`) | Destino | Status | Artefato / Evidência verificada |
|---|---|---|---|---|
| **INT-01** | Cenário da rede, matriz/unidades e canais App, Totem e Web | C | Atendido | `RU_Projeto_Back_End.PDF` (Seção 1) e `README.md` |
| **INT-02** | Objetivos, Fluxo A, prioridades e separação implementado vs. proposto | C | Atendido | `RU_Projeto_Back_End.PDF` (Seção 1, quadro de escopo) |
| **RF-01** | Cadastro, login, usuários e perfis/roles | I+C | Atendido | `identidade/**`, `AutorizacaoIntegracaoTest` e `T01`/`T10`/`T11` |
| **RF-02** | Gestão e consulta de unidades da rede | I+C | Atendido | `CatalogoController`, `UnidadeEntity` e `T14` (`404`) |
| **RF-03** | Cardápio e produtos por unidade com disponibilidade | I+C | Atendido | `ConsultarCatalogoService`, `EstoqueIntegracaoTest` e `T02` |
| **RF-04** | Pedido com itens, preços congelados, total e status | I+C | Atendido | `CriarPedidoService`, `ConsultarPedidoService` e `T03` |
| **RF-05** | Transições `EM_PREPARO` → `PRONTO` → `ENTREGUE` e cancelamento | I+C | Atendido | `TransicionarPedidoService`, `PedidoStatusMachine` e `T05`/`T06` |
| **RF-06** | Estoque por unidade, entrada/saída e bloqueio sem saldo | I+C | Atendido | `EstoqueService`, `ReservaEstoqueService`, `EstoqueDomainTest` e `T15` (`409`) |
| **RF-07** | Fidelização: pontos e resgate simples mediante consentimento | I+C | Atendido | `FidelidadeService`, `RegraFidelidade`, `FidelidadeIntegracaoTest` e `T07`/`T08` |
| **RF-08** | Promoções/campanhas (regra e precedência conceitual) | C | Atendido | `etapa-02/modelagem-fluxo-a.md` (Seção 9) e `RU_Projeto_Back_End.PDF` (Seção 2.1) |
| **RF-09** | Solicitação e registro de pagamento externo mock | I+C | Atendido | `PaymentGateway`, `MockPaymentGateway`, `PagamentoService` e `T04`/`T16` |
| **MUL-01** | `canalPedido` ENUM obrigatório (`APP`, `TOTEM`, `BALCAO`, `PICKUP`, `WEB`) | I+C | Atendido | `CanalPedido`, `CriarPedidoService` e `T03`/`T12` (`422`) |
| **MUL-02** | Consulta/filtro por `canalPedido` e relatório por unidade/canal | I+C | Atendido | `ListarPedidosService`, `RelatorioPedidosService` e `T09`/`T18` |
| **RNF-01** | Segurança: JWT, roles, BCrypt e LGPD | I+C | Atendido | `SecurityConfiguration`, `JwtService`, `JwtAuthenticationFilter` |
| **RNF-02** | Logs/auditoria de ações sensíveis | I+C | Atendido | `RegistrarAuditoriaService`, `ConsultarAuditoriaService` e `T17` |
| **RNF-03** | Desempenho em pico e disponibilidade (índices, lote, paginação) | C | Atendido | `V3`/`V6`/`V10`/`V11`, `EstoqueIntegracaoTest` (Hibernate Statistics) e `RU_Projeto_Back_End.PDF` (Seção 2.2) |
| **RNF-04** | Tolerância a falhas na integração de pagamento (`202` + reconciliação) | I+C | Atendido | `PagamentoService`, `FluxoPedidoIntegracaoTest` e requisição Postman 22–23 |
| **RNF-05** | Documentação OpenAPI/Swagger real | I+C | Atendido | `backend/src/main/resources/static/openapi.yaml` e `/swagger-ui.html` |
| **DIA-01** | Diagrama de Casos de Uso com os 5 grupos de atores | C | Atendido | `etapa-11/diagramas/diagrama-casos-de-uso.png` e `RU_Projeto_Back_End.PDF` (Figura 1) |
| **DIA-02** | Descrição da Feature Crítica (fluxo, pré/pós-condições, exceções, regras) | C | Atendido | `etapa-02/modelagem-fluxo-a.md` (Seção 2) e `RU_Projeto_Back_End.PDF` (Seção 3.1) |
| **DIA-03** | DER com entidades, PK/FK, cardinalidades e restrições exportado | C | Atendido | `etapa-11/diagramas/der-modelo-dados.{pdf,png}` e `RU_Projeto_Back_End.PDF` (Figura 2) |
| **DIA-04** | Arquitetura `API`/`Application`/`Domain`/`Infrastructure` | I+C | Atendido | Pacotes Java em `br.com.raizes.*`, `diagrama-arquitetura-classes.png` (Figura 3) |
| **DIA-05** | Diagrama de Classes de domínio | C | Atendido | `etapa-11/diagramas/diagrama-arquitetura-classes.png` e `etapa-02/modelagem-fluxo-a.md` |
| **DIA-06** | Diagrama de Sequência do fluxo crítico | C | Atendido | `etapa-11/diagramas/diagrama-sequencia-fluxo-a.png` (Figura 4) |
| **API-01** | Catálogo de endpoints agrupado por recurso | C | Atendido | `etapa-02/contratos-api.md`, `backend/README.md` e `RU_Projeto_Back_End.PDF` (Seção 4) |
| **API-02** | Checklist de 8 itens para cada endpoint | I+C | Atendido | `etapa-02/contratos-api.md` e `openapi.yaml` |
| **API-03** | Convenções REST: plural, ID no path, paginação e status codes | I+C | Atendido | Todos os controllers + `PageEnvelope` |
| **API-04** | Erro JSON uniforme (`ApiError`) | I+C | Atendido | `ApiError`, `ApiExceptionHandler`, `SecurityErrorWriter` |
| **API-05** | Exigência e filtro por `canalPedido` | I+C | Atendido | `CriarPedidoService`, `ListarPedidosService` e `T03`/`T09`/`T12` |
| **LGP-01** | Inventário de dados pessoais, finalidade e base legal | C | Atendido | `etapa-02/modelagem-fluxo-a.md` (Seção 8.1) e `RU_Projeto_Back_End.PDF` (Seção 5) |
| **LGP-02** | Consentimento específico registrado e revogável para fidelidade | I+C | Atendido | `ConsentimentoEntity`, `FidelidadeController`, `FidelidadeIntegracaoTest` |
| **LGP-03** | Hash BCrypt, JWT, roles, minimização e respostas sem dados sensíveis | I+C | Atendido | `IdentidadeService`, `AutorizacaoIntegracaoTest` |
| **LGP-04** | Log de acesso sensível e estratégia de retenção/anonimização | I+C | Atendido | `AuditoriaController`, `etapa-02/modelagem-fluxo-a.md` (Seção 8.3) |
| **ENT-01** | Repositório Git organizado em camadas com histórico de commits | I+C | Atendido | Repositório Git em `C:\projeto-raizes` com 7 commits evolutivos (pronto para `git push`) |
| **ENT-02** | README reproduzível + `.env.example` sem segredos | C | Atendido | `README.md`, `backend/README.md` e `backend/.env.example` |
| **ENT-03** | OpenAPI real com exemplos e códigos | I+C | Atendido | `backend/src/main/resources/static/openapi.yaml` (`200 OK` em `/openapi.yaml`) |
| **ENT-04** | Coleção Postman `.json` com login/token, fluxo e erros | I+C | Atendido | `backend/postman/raizes-fluxo-a.postman_collection.json` (7 pastas, 38 reqs) |
| **ENT-05** | PostgreSQL persistente, DER compatível, JPA `validate` e Flyway `V1`..`V11` | I+C | Atendido | `db/migration/V1..V11`, `db/demo/R__demo_seed.sql`, `application.yml` |
| **ENT-06** | Fluxo A completo e testável ponta a ponta | I+C | Atendido | `FluxoPedidoIntegracaoTest`, `FidelidadeIntegracaoTest` e coleção Postman |
| **ENT-07** | Autenticação + autorização e erro padrão no MVP | I+C | Atendido | `SecurityConfiguration`, `AutorizacaoIntegracaoTest` |
| **ENT-08** | Links e evidências no README (Swagger local, coleção, DER, repositório) | C | Atendido | `backend/README.md` (seção inicial de evidências) e `RU_Projeto_Back_End.PDF` (Seção 6) |
| **TES-01** | Plano com ≥10 cenários (entregues **18**: 11 positivos e 7 negativos) | I+C | Atendido | `etapa-10/plano-de-testes.md`, `backend/README.md` e `RU_Projeto_Back_End.PDF` (Seção 7) |
| **TES-02** | Cada caso com ID, rota, pré-condição, entrada, saída e nome na coleção | C | Atendido | `etapa-10/plano-de-testes.md` (Seção 3) |
| **TES-03** | Cobertura de login (`200`), sem token (`401`) e sem perfil (`403`) | I+C | Atendido | `T01`, `T10`, `T11` + `AutorizacaoIntegracaoTest` |
| **TES-04** | Cobertura de campo obrigatório ausente e tipo/formato inválido (`422`) | I+C | Atendido | `T12`, `T13` + `PedidoIntegracaoTest` |
| **TES-05** | Cobertura de pedido válido (`201`), inexistente (`404`) e sem estoque (`409`) | I+C | Atendido | `T03`, `T14`, `T15` + `PedidoIntegracaoTest` |
| **TES-06** | Cobertura de pagamento mock aprovado e recusado | I+C | Atendido | `T04`, `T16` + `FluxoPedidoIntegracaoTest` |
| **TES-07** | Cobertura de auditoria de ação sensível | I+C | Atendido | `T17` + `AuditoriaERelatorioIntegracaoTest` |
| **ABN-01** | PDF único com capa, folha de rosto, sumário, seções do roteiro e referências | C | Atendido | `RU_Projeto_Back_End.PDF` (15 páginas) |
| **ABN-02** | Formatação ABNT e nomenclatura `<RU>_Projeto_Back_End.PDF` | C | Atendido | `RU_Projeto_Back_End.PDF` (pronto para renomear `RU` com o número de matrícula do aluno) |
| **ABN-03** | Apresentação técnica profissional e instruções de publicação dos links | C | Atendido | `RU_Projeto_Back_End.PDF`, `README.md` e `backend/README.md` |
| **CON-01** | Conclusão distingue implementado/proposto e justifica prioridades | C | Atendido | `RU_Projeto_Back_End.PDF` (Seção 8) |
| **CON-02** | Conexão explícita DER/classes/casos de uso ↔ endpoints/fluxo/mock/LGPD/testes | C | Atendido | `RU_Projeto_Back_End.PDF` (Seção 8) |
| **ACA-01** | Declaração obrigatória de uso de IA (ferramenta, prompts, trechos) | C | Atendido | `RU_Projeto_Back_End.PDF` (Seção 8.1) |

## 5. Pendências externas (exclusivas do estudante antes do envio no AVA)

1. **Preencher o RU real e renomear o PDF:** substituir `RU` no nome do arquivo `C:\projeto-raizes\RU_Projeto_Back_End.PDF` pelo número de Registro Único do estudante (ex.: `4999657_Projeto_Back_End.PDF`). Caso deseje atualizar o texto da capa/folha de rosto com nome e RU, basta editar as duas constantes em `generate_abnt_pdf.py` e reexecutar `python generate_abnt_pdf.py`.
2. **Publicar o repositório Git remoto:** o repositório Git local em `C:\projeto-raizes` já está inicializado com histórico de 7 commits limpos (sem segredos nem binários locais). Basta criar um repositório público vazio no GitHub/GitLab/Bitbucket, executar `git remote add origin <URL>` e `git push -u origin main`, e registrar a URL pública no README/PDF antes da postagem no AVA.
