# Etapa 02 — validação e passagem para a tarefa 03

**Nota histórica (etapa 07):** a tabela abaixo registra a modelagem aprovada na etapa 02. A regra posterior exigida pelo solicitante substituiu `PAGAMENTO_FALHOU` com liberação imediata por `PAGAMENTO_INDETERMINADO` com reserva retida até reconciliação. Consulte `modelagem-fluxo-a.md`, `contratos-api.md` revisados e `etapa-07/validacao.md` para o estado executável atual.

## Base conferida antes da modelagem

- Lidos `etapa-01/estado-inicial.md`, `etapa-01/matriz-rastreabilidade.md` e `etapa-01/decisoes-e-pendencias.md`.
- Fonte PDF conferida novamente: 23 páginas e SHA-256 `DFEA9533ED57BF5BB67282800937B2C9C9A27AB6D34F82373C5D2DFF74588B17`.
- A matriz contém 54 IDs únicos e destina as nove seções obrigatórias: introdução; análise/requisitos; diagramas; API/contratos; LGPD; entrega técnica; testes; ABNT; conclusão. A declaração de uso de IA está em `ACA-01`.
- A mudança de local de trabalho foi resolvida pelo solicitante: todos os novos arquivos da etapa 02 estão em `C:\projeto-raizes`.

## Pendências de domínio resolvidas nesta etapa

As propostas da etapa 01 foram fechadas em `modelagem-fluxo-a.md`: reserva e baixa de estoque por unidade; uma tentativa de pagamento mock idempotente; estados e terminais; cancelamento somente antes da tentativa; cliente sem cadastro apenas via credencial de Totem/Atendente; `canalPedido` separado de `modalidadeAtendimento`. Essas decisões estão refletidas no DER, no caso de uso, nos diagramas e nos contratos. O roteiro não fixava esses detalhes; são escolhas de projeto para a tarefa 03.

## Conferência cruzada

| Critério | Evidência na etapa 02 | Resultado |
|---|---|
| Atores exigidos | Casos de uso: Cliente (App/Web/Totem), Atendente, Cozinha, Gerente/Administrador e Gateway mock | Coberto |
| Feature crítica | UC-A com atores, gatilho, pré-condições, fluxo, pós-condições, exceções e regras | Coberto |
| Diagramas | Casos de uso, estados, DER, classes, sequência e módulos/camadas em seis blocos Mermaid | Coberto no texto; renderização visual pendente |
| DER | PK/FK, cardinalidades, chaves compostas, unicidade, checks, índices e vínculo de estoque por unidade | Coberto no modelo lógico; migrations pendentes |
| Pedido ↔ dados | `unidade`, itens, preços congelados, total, canal, modalidade, cliente opcional, criador e status aparecem no DER e no contrato de criação/consulta | Coerente |
| Pagamento ↔ estado | 0..1 tentativa, chave idempotente; aprovação → `EM_PREPARO`, recusa → `PAGAMENTO_RECUSADO`, falha → `PAGAMENTO_FALHOU` | Coerente |
| Estoque ↔ estado | Criação reserva; aprovação baixa; recusa/falha/cancelamento libera; saldo disponível é físico menos reservado | Coerente |
| Operação ↔ permissões | Cozinha marca `PRONTO`; Atendente marca `ENTREGUE`; Gerente atua na própria unidade; aprovação não é controlada por `PATCH /status` | Coerente |
| Multicanalidade | `canalPedido` obrigatório no POST e filtrável no GET paginado; `modalidadeAtendimento` é campo próprio | Coerente |
| Contratos | 11 endpoints detalhados, cada um com finalidade, método/rota, permissão, parâmetros, request, response, códigos e erro | Coberto |
| Exemplos | 19 blocos JSON analisados sintaticamente | Todos válidos |
| Rastreabilidade | Referências de IDs nos arquivos da etapa 02 confrontadas com a matriz | Nenhum ID desconhecido |
| Escopo da etapa | Busca por controllers/arquivos Java | Nenhum criado |

As descrições Mermaid foram revisadas contra a [sintaxe oficial de DER](https://mermaid.js.org/syntax/entityRelationshipDiagram.html) e de [classes](https://mermaid.js.org/syntax/classDiagram). Não havia renderizador Mermaid local; portanto não se afirma validação visual dos diagramas.

## Ambiente e pendências restantes

- Java 21.0.12.1 está disponível. `mvn` e `psql` não foram encontrados no PATH; também não foi localizado serviço PostgreSQL. Para a tarefa 03, preparar Maven Wrapper e PostgreSQL local, além de validar conexão, migrations e execução. Nenhuma API ou teste de integração foi executado nesta etapa documental.
- A versão 4.1.x existe: o [anúncio oficial do Spring Boot 4.1.3](https://spring.io/blog/2026/11/20/spring-boot-4-1-3-available-now/) informa disponibilidade no Maven Central em 20/11/2026. A tarefa 03 pode fixar 4.1.3 no `pom.xml` e verificar resolução efetiva das dependências.
- RU, normas ABNT do AVA, links públicos, OpenAPI executável, coleção Postman/Insomnia, testes e declaração final de IA pertencem à entrega posterior. Não bloqueiam os diagramas e contratos desta etapa.
- `RF-07` (fidelização com consentimento), `RF-08` (promoções), cadastro administrativo e CRUD completo de catálogo/unidades seguem com destino conceitual ou posterior na matriz. O MVP da tarefa 03 deve implementar o fluxo A completo antes de ampliar escopo.

**Resultado:** requisitos do Fluxo A, diagramas, estados, dados e contratos estão alinhados como especificação para a tarefa 03. O alinhamento ainda precisa ser confrontado com código e comportamento executável quando forem implementados.
