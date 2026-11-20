# Etapa 01 — decisões e pontos de confirmação

O diretório indicado não continha documentação de decisões anteriores. A coluna **origem** impede atribuir ao solicitante escolhas que não estavam no pedido ou no roteiro. As propostas abaixo fixam uma base revisável para a tarefa 02, sem afirmar que já havia código ou consenso prévio.

| Tema | Registro para a tarefa 02 | Origem / estado |
|---|---|---|
| Stack e organização | Java 21; Spring Boot 4.1.x; Maven; PostgreSQL local; Spring Security; JPA; Flyway; sem Docker/Testcontainers; monólito modular API, Application, Domain, Infrastructure. | Definido pelo solicitante. |
| Fluxo principal | Pedido → Pagamento mock → Atualização de status. | Definido pelo solicitante; coincide com fluxo A do roteiro. |
| Estoque | Saldo e movimentos por unidade. Na criação do pedido, validar disponibilidade e reservar quantidade na mesma transação; ao recusar pagamento ou cancelar antes do preparo, liberar a reserva. Confirmar a baixa após aprovação. Impedir saldo negativo e venda simultânea acima do saldo. | Roteiro exige estoque por unidade e bloqueio por indisponibilidade. Momento exato da reserva/baixa é **proposta de projeto**, a confirmar antes da implementação. |
| Pagamento | Adaptador externo simulado, sem cobrança real. Persistir solicitação, resposta e status por tentativa; contemplar aprovação, recusa e falha técnica. Usar resultado controlável para testes e chave de idempotência para evitar duplicidade. | Mock e registro são exigidos pelo roteiro; desenho da simulação/idempotência é **proposta de projeto**. |
| Estados | Proposta de estados: `AGUARDANDO_PAGAMENTO` → `EM_PREPARO` → `PRONTO` → `ENTREGUE`; recusa leva a `PAGAMENTO_RECUSADO`; `CANCELADO` é terminal. Rejeitar transições não previstas. Regras de cancelamento após pagamento e eventual nova tentativa após recusa precisam ser fechadas antes dos contratos. | Roteiro exige cozinha/pronto/entregue/cancelado e retorno do mock; nomes e transições intermediárias são **proposta de projeto**. |
| Cliente anônimo | Proposta: `clienteId` opcional no pedido para permitir compra sem cadastro. Fidelização e resgate dependem de cliente identificado e consentimento. A consulta do pedido anônimo deve exigir identificador não adivinhável ou credencial apropriada, sem expor lista de pedidos de terceiros. | O roteiro mostra `clienteId` em exemplo, mas não fixa compra anônima. **Decisão ainda não comprovada pela fonte; confirmar**. |
| Canal versus modalidade | `canalPedido` é dado de origem obrigatório, com `APP`, `TOTEM`, `BALCAO`, `PICKUP`, `WEB`, persistido e filtrável. Modalidade de atendimento/retirada é conceito separado; não inferir modalidade a partir do canal. `PICKUP` permanece como valor do enum do roteiro, ainda que o nome possa sugerir retirada. | Campo e valores vêm do roteiro. A separação entre origem e modalidade é **proposta de modelagem**; nomenclatura da modalidade fica para tarefa 02. |

## Pendências para a tarefa 02

1. Obter uma raiz de trabalho gravável que corresponda ao projeto indicado ou transferir estes três registros para lá. A sessão atual permite gravação apenas em `C:\projeto-raizes`.
2. Confirmar as quatro propostas de domínio acima (estoque, estados, anonimato, canal/modalidade) antes de congelar DER e contratos. O pagamento mock já está decidido; detalhes operacionais ainda são propostas.
3. Verificar a versão efetivamente disponível do Spring Boot 4.1.x no início da implementação, sem alterar a stack fixada pelo solicitante.
4. Obter RU e normas ABNT específicas disponíveis no AVA antes de finalizar o PDF acadêmico. Os links públicos e evidências executáveis só poderão ser validados após a implementação.
5. Preparar declaração de uso de IA no documento final, conforme instrução do próprio roteiro.

Nenhum controller foi iniciado nesta etapa.
