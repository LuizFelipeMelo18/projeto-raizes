# Etapa 01 — matriz de rastreabilidade do roteiro Back-End

Fonte: `roteiro da atividade.pdf`, 23 páginas. As páginas citadas abaixo são as páginas físicas do PDF (a numeração impressa começa na página física 2).

**Atualização final na etapa 11:** todos os requisitos de código, persistência, segurança/LGPD, fidelidade, auditoria, relatórios, OpenAPI, coleção Postman e documento acadêmico ABNT foram consolidados e verificados; o quadro de conferência final item a item consta da seção final deste arquivo e de `../etapa-11/validacao.md`.

**Prioridade:** P0 = indispensável para o fluxo executável e a avaliação mínima; P1 = obrigatório na entrega completa; P2 = recomendado ou complementar. **Destino:** I = implementação e evidência executável futuras; C = documentação conceitual futura; I+C = ambas. O roteiro admite descrição conceitual de parte dos RF, mas exige um fluxo completo com persistência, autenticação, autorização e erro padrão. A escolha do fluxo A e da stack veio do solicitante.

| ID | Seção / requisito rastreado | Prio. | Destino | Artefato esperado | Verificação prevista |
|---|---|---|---|---|---|
| INT-01 | 1. Introdução: cenário da rede, matriz/unidades e atendimento App, Totem e Web (p. 4, 14) | P1 | C | Seção “Introdução e objetivos” do PDF final | Texto situa rede, unidades, canais e problema de Back-end |
| INT-02 | 1. Objetivos, escopo, fluxo A, prioridades e justificativas (p. 4, 14) | P0 | C | Introdução e quadro de escopo | Fluxo escolhido e separação implementado/proposto aparecem expressamente |
| RF-01 | 2. Cadastro, login, usuários e perfis/roles (p. 5) | P0 | I+C | Módulo de identidade, contrato `/auth`, modelo de usuário | Login com token; acesso sem token 401 e perfil indevido 403 |
| RF-02 | 2. Gestão e consulta de unidades da rede (p. 5) | P1 | I+C | Modelo Unidade e catálogo/contrato de consulta | Pedido referencia unidade válida; unidade inexistente gera 404 |
| RF-03 | 2. Cardápio e produtos por unidade (p. 5) | P1 | I+C | Modelo Produto/Cardápio e contrato de consulta | Consulta filtra por unidade e não expõe item indisponível como vendável |
| RF-04 | 2. Pedido com itens, preços, total e status, criação e consulta (p. 5) | P0 | I+C | Pedido/ItemPedido persistidos e contrato `/pedidos` | Criação 201, valores calculados no servidor e leitura consistente |
| RF-05 | 2. Transições cozinha → pronto → entregue e cancelamento (p. 5) | P0 | I+C | Regras de estado e contrato de alteração | Transições permitidas aceitas; inválidas resultam em erro 409 |
| RF-06 | 2. Estoque por unidade, entrada/saída e bloqueio de venda sem saldo (p. 5) | P0 | I+C | Modelo de saldo/movimento, regra de disponibilidade; contrato de movimentação no escopo completo | Pedido sem disponibilidade gera 409; saldo é por unidade; movimentos são auditáveis |
| RF-07 | 2. Fidelização: pontos e resgate simples mediante consentimento (p. 5) | P1 | C | Regras e modelo conceitual de pontos/consentimento; eventual módulo futuro | Documento explicita elegibilidade, acúmulo, resgate e ausência sem consentimento |
| RF-08 | 2. Promoções/campanhas (p. 5) | P1 | C | Regra conceitual de aplicação e precedência | Regra documenta elegibilidade, vigência, cálculo e impacto no total |
| RF-09 | 2. Solicitação e registro de pagamento externo **mock**, aprovado/recusado, retorno ao cliente (p. 5) | P0 | I+C | Porta de pagamento, adaptador mock, registro de tentativa e contrato `/pagamentos` | Respostas de aprovação e recusa, payload e status coerentes; nenhuma cobrança real |
| MUL-01 | 2. `canalPedido` como ENUM obrigatório: APP, TOTEM, BALCAO, PICKUP, WEB (p. 5, 8) | P0 | I+C | Campo de domínio/persistência e contrato de criação | Campo gravado; ausência/valor inválido gera 400/422 padrão |
| MUL-02 | 2. Consulta/filtro por `canalPedido`, rastreabilidade operacional (p. 5, 8) | P0 | I+C | Contrato de listagem paginada e consulta por canal | `?canalPedido=TOTEM` retorna só o canal pedido |
| RNF-01 | 2. Segurança: token, controle de acesso, hash de senha e LGPD (p. 6) | P0 | I+C | Configuração Spring Security e política de privacidade | Senhas irreversíveis, 401/403 coerentes, respostas sem dados sensíveis |
| RNF-02 | 2. Logs/auditoria de ações sensíveis (p. 6, 12) | P1 | I+C | Eventos/logs para criação, cancelamento e status | Pelo menos um cenário prova emissão sem expor dados pessoais indevidos |
| RNF-03 | 2. Desempenho em pico e disponibilidade (p. 6) | P1 | C | Metas e estratégia conceitual de paginação/índices/operação | Documento define como medir e limitações do ambiente local |
| RNF-04 | 2. Falhas de integração de pagamento (p. 6) | P0 | I+C | Política de timeout, erro, idempotência e consistência | Simulação de recusa/falha não deixa pedido como pago indevidamente |
| RNF-05 | 2. Documentação OpenAPI/Swagger (p. 6) | P0 | I+C | Especificação alinhada à API implementada | Swagger lista endpoints reais, exemplos e códigos de resposta |
| DIA-01 | 3. Casos de uso: Cliente, Atendente, Cozinha, Gerente/Admin e Gateway (p. 6) | P1 | C | Diagrama de casos de uso | Todos os atores e relações com o fluxo estão representados |
| DIA-02 | 3. Feature crítica com fluxo, pré/pós-condições, exceções e regras, incluindo idempotência/estoque/pagamento negado (p. 6) | P1 | C | Descrição de caso de uso “Realizar pedido e solicitar pagamento” | Percursos principal e alternativos batem com regras e testes |
| DIA-03 | 3. DER: entidades, atributos, PK/FK, cardinalidades e restrições (p. 6, 10) | P0 | C | DER exportado em imagem/PDF | Pedido–itens–pagamento e estoque próprio por unidade conferem com migrations |
| DIA-04 | 3. Arquitetura API/Application/Domain/Infrastructure e responsabilidades (p. 6) | P0 | I+C | Diagrama de componentes/pacotes e estrutura Maven | Dependências entre camadas e localização de casos de uso são coerentes |
| DIA-05 | 3. Diagrama de classes do domínio (p. 7) | P1 | C | Diagrama de classes | Entidades, objetos e relações conferem com DER/regras |
| DIA-06 | 3. Sequência ou atividade Pedido → Pagamento → Status (recomendado, p. 7) | P2 | C | Diagrama de sequência/atividade | Etapas, retornos e falhas coincidem com fluxo e contratos |
| API-01 | 4. Catálogo de endpoints agrupado por recurso (p. 7–8) | P1 | C | Tabela de rotas `/auth`, `/usuarios`, `/unidades`, `/produtos`, `/estoque`, `/pedidos`, `/pagamentos`, `/fidelidade` | Cada recurso obrigatório tem rota implementada ou proposta explicitamente identificada |
| API-02 | 4. Contrato por endpoint: propósito, método/rota, auth/role, path/query, JSON entrada/saída, status e erros (p. 8) | P0 | I+C | OpenAPI e documento de contratos | Checklist de oito campos completo para cada endpoint entregue |
| API-03 | 4. Convenções: plural, ID no path, paginação, status 200/201/204/400/401/403/404/409/422 (p. 8) | P0 | I+C | Convenções da API e OpenAPI | Revisão das rotas e respostas reais, inclusive paginação em listas |
| API-04 | 4. Erro JSON uniforme (p. 8, 21) | P0 | I+C | Esquema de erro e tratamento global | Falhas de validação, auth, não encontrado e conflito compartilham esquema |
| API-05 | 4. Pedido exige `canalPedido` e listagem filtra por canal/status (p. 8–9) | P0 | I+C | Contratos de criação e listagem | Testes de campo ausente/inválido e filtro APP/TOTEM |
| LGP-01 | 5. Inventário de dados pessoais, finalidade e base legal (p. 9) | P1 | C | Quadro LGPD por dado e finalidade | Cada dado coletado tem finalidade e base legal descritas; sem base inventada como fato jurídico |
| LGP-02 | 5. Consentimento registrado para fidelização (p. 5, 9) | P1 | I+C | Modelo/registro de consentimento quando fidelidade for implementada; regra conceitual no MVP | Sem consentimento, pontos/resgate não são aplicados |
| LGP-03 | 5. Hash, token, roles, minimização e respostas seguras (p. 9) | P0 | I+C | Segurança e política de DTOs | Testes 401/403; senha e dados sensíveis não constam em payloads/logs |
| LGP-04 | 5. Log de acesso sensível e retenção/anonimização (p. 9) | P1 | I+C | Eventos de auditoria e política documentada de retenção | Evento contém ator/ação/data sem conteúdo excessivo; política revisável |
| ENT-01 | 6. Repositório público com código organizado e histórico de evolução (p. 9) | P1 | I+C | Repositório Git público; commits com progresso (cinco recomendados) | URL acessível e histórico revisável na entrega |
| ENT-02 | 6. README reproduzível: Java, Maven, PostgreSQL local, variáveis, instalação, banco/migrations/seed, execução, Swagger e testes (p. 9) | P0 | C | `README.md` e `.env.example` sem segredos | Pessoa terceira executa passos em máquina local sem Docker |
| ENT-03 | 6. OpenAPI real com exemplos e códigos (p. 9) | P0 | I+C | Swagger/OpenAPI | Especificação acessível e consistente com endpoints executáveis |
| ENT-04 | 6. Coleção Postman/Insomnia `.json` com login/token, fluxo e erro (p. 10) | P0 | I+C | Coleção versionada | Importação e execução reproduzem fluxo mínimo |
| ENT-05 | 6. PostgreSQL persistente, DER compatível, JPA e Flyway (p. 10) | P0 | I+C | Entidades/repositórios, migrations e DER | Subida do banco do zero; dados sobrevivem a reinício da API |
| ENT-06 | 6. Fluxo A completo: criar pedido, validar itens, registrar mock e atualizar status (p. 10) | P0 | I+C | Casos de uso e evidência de ponta a ponta | Coleção comprova transição e registro persistido |
| ENT-07 | 6. Autenticação + autorização e erro padrão no MVP (p. 10) | P0 | I+C | Configuração de segurança e exceções | 401, 403 e erro padrão em execução |
| ENT-08 | 6. Links/evidências: repositório público, Swagger local/rota e coleção; deploy opcional (p. 10) | P1 | C | Seção de evidências no README/PDF | Links resolvem e instrução de Swagger local é reproduzível; coleção existe |
| TES-01 | 7. Plano com ≥10 casos, ≥6 positivos e ≥4 negativos (p. 11) | P0 | I+C | Tabela T01… e coleção organizada | Contagem e correspondência 1:1 entre casos e requisições |
| TES-02 | 7. Cada caso: ID, endpoint/método, pré-condição, entrada, status/response e evidência (p. 11) | P0 | C | Plano de testes | Campos completos em todos os casos |
| TES-03 | 7. Auth: login, 401 sem token e 403 sem perfil (p. 11) | P0 | I+C | Requisições de coleção e asserções | Três respostas observadas com códigos e esquema corretos |
| TES-04 | 7. Validações: campo obrigatório e tipo/formato inválido (p. 12) | P0 | I+C | Casos negativos | 400/422 com campo e motivo padronizados |
| TES-05 | 7. Pedido: válido, unidade/produto ausente e estoque insuficiente (p. 12) | P0 | I+C | Casos positivos/negativos | 201, 404 e 409 observados |
| TES-06 | 7. Pagamento mock aprovado/recusado e status coerente (p. 12) | P0 | I+C | Casos de pagamento | Registro e estado persistidos refletem ambos os retornos |
| TES-07 | 7. Auditoria de ao menos uma ação sensível (p. 12) | P1 | I+C | Caso de auditoria e evidência de log | Registro gerado e correlacionável; caso não implementado, declaração explícita e justificativa |
| ABN-01 | 8. Entrega acadêmica em PDF único com capa, sumário, seções e referências (p. 13) | P1 | C | PDF final com estrutura ABNT | Inspeção visual e conferência de seções/referências |
| ABN-02 | 8. Normas ABNT do AVA e nome `<RU>_Projeto_Back_End.PDF` (p. 13) | P1 | C | PDF final renomeado com RU real | Checklist contra normas da biblioteca do AVA; nome validado |
| ABN-03 | 8. Apresentação profissional e links públicos funcionais (p. 10, 13) | P1 | C | PDF/README finais | Revisão de clareza, execução e acesso externo aos links |
| CON-01 | 9. Conclusão distingue implementado/proposto e justifica prioridades (p. 13) | P1 | C | Seção “Conclusão” do PDF final | Afirmações conferem com código, contratos e evidências reais |
| CON-02 | 9. Conexão DER/classes/casos de uso ↔ endpoints/fluxo; mock, validações/erros, LGPD e testes (p. 13) | P1 | C | Síntese final com referências internas | Cada elo aponta para artefato existente e resultado testado |
| ACA-01 | Regra transversal: declarar uso de IA, ferramenta, prompts e trechos aproveitados (p. 4) | P1 | C | Declaração no final do documento acadêmico | Declaração presente e fiel ao processo realizado |

## Conferência das nove seções obrigatórias

| Seção do roteiro | IDs de destino | Situação nesta etapa |
|---|---|---|
| Introdução e objetivos | INT-01–02 | Destino definido |
| Análise e requisitos | RF-01–09, MUL-01–02, RNF-01–05 | Destino definido |
| Diagramas | DIA-01–06 | Destino definido |
| API e endpoints | API-01–05 | Destino definido |
| LGPD, privacidade e segurança | LGP-01–04 | Destino definido |
| Entrega técnica | ENT-01–08 | Destino definido |
| Plano de testes | TES-01–07 | Destino definido |
| Critérios de entrega e ABNT | ABN-01–03 | Destino definido |
| Conclusão | CON-01–02 | Destino definido |

As seções “Orientação final”, “Critérios de avaliação” e “Anexos” foram usadas como critérios transversais e modelos, mas não constituem três seções adicionais obrigatórias do trabalho. A regra de declaração de IA está em ACA-01.

## Status final na etapa 11 (conferência de entrega)

- **Implementados e validados com persistência PostgreSQL 19 (`V1` a `V11` + `R__demo_seed.sql`), 35 testes JUnit e 38 requisições Postman (`T01`..`T18`):** `RF-01`, `RF-02`, `RF-03`, `RF-04`, `RF-05`, `RF-06`, `RF-07` (promovido de `C` para `I+C` na etapa 08), `RF-09`, `MUL-01`, `MUL-02`, `RNF-01`, `RNF-02`, `RNF-04`, `RNF-05`, `DIA-04`, `API-02`, `API-03`, `API-04`, `API-05`, `LGP-02`, `LGP-03`, `LGP-04`, `ENT-03`, `ENT-04`, `ENT-05`, `ENT-06`, `ENT-07`, `TES-01`, `TES-03`, `TES-04`, `TES-05`, `TES-06`, `TES-07`.
- **Documentados, diagramados e consolidados no PDF ABNT (`RU_Projeto_Back_End.PDF`) e nos artefatos do repositório:** `INT-01`, `INT-02`, `RF-08` (evolução conceitual de promoções/campanhas), `RNF-03`, `DIA-01`, `DIA-02`, `DIA-03` (`etapa-11/diagramas/der-modelo-dados.{png,pdf}`), `DIA-05`, `DIA-06`, `API-01`, `LGP-01`, `ENT-01` (repositório Git local com histórico de evolução pronto para push público), `ENT-02`, `ENT-08`, `TES-02`, `ABN-01`, `ABN-02`, `ABN-03`, `CON-01`, `CON-02`, `ACA-01`.
