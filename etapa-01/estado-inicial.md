# Etapa 01 — estado inicial observado

Inspeção realizada em 20/11/2026, antes de criar qualquer artefato desta etapa.

## Origem e limites

- Projeto indicado: `C:\Users\felip\Documents\Codex\2026-11-20\video-de-explica-o-do-conceito`.
- Fonte: `C:\Users\felip\Downloads\roteiro da atividade.pdf`, trilha Back-End, 23 páginas, SHA-256 `DFEA9533ED57BF5BB67282800937B2C9C9A27AB6D34F82373C5D2DFF74588B17`.
- A raiz gravável desta sessão é `C:\projeto-raizes`. O projeto indicado está fora dela. Por isso, os registros desta etapa foram criados em `C:\projeto-raizes\etapa-01`; o diretório indicado foi apenas lido.
- O roteiro permite IA para organizar texto e explicar conceitos gerais, mas proíbe gerar a solução ou o código final. Esta etapa registra inventário, requisitos e decisões de planejamento. A entrega acadêmica futura deverá declarar ferramenta, prompts e trechos aproveitados se houver uso de IA.

## Inventário do projeto indicado

| Caminho relativo | Tipo | Bytes | Modificado (UTC−03:00) | SHA-256 |
|---|---|---:|---|---|
| `outputs/` | diretório, vazio | — | 2026-11-20 12:20:21 | — |
| `work/` | diretório | — | 2026-11-20 12:48:35 | — |
| `work/pdf_review/` | diretório | — | 2026-11-20 12:24:42 | — |
| `work/roteiro_back_end_auditoria.txt` | arquivo | 38.451 | 2026-11-20 12:48:36 | `9FFFA48241011426DB8E76FD026DB6D489A5AA65B668EA7C9ED07E13FD25FA02` |
| `work/pdf_review/conceito.png` | arquivo, 827 × 1170 px | 175.866 | 2026-11-20 12:24:42 | `579FC98A0CCED6B4094319562E29A704F7209F2EA1D09C226F07F4B6AA81E424` |
| `work/pdf_review/criterios.png` | arquivo, 1985 × 1404 px | 353.860 | 2026-11-20 12:24:42 | `45AFB3C520BF8DC8D0564D607F0E9F4E793945AC1E7FB1D3EB10A827E62BFC17` |

`git status` no projeto indicado retornou “not a git repository”. Não havia `pom.xml`, `src/`, README, migrations, testes nem controllers. Nenhum arquivo existente foi alterado nesta etapa.

## Base técnica definida pelo solicitante

Java 21, Spring Boot 4.1.x, Maven, PostgreSQL local, Spring Security, JPA e Flyway; sem Docker e sem Testcontainers. Monólito modular nas camadas API, Application, Domain e Infrastructure. Fluxo principal: Pedido → Pagamento mock → Atualização de status. Nesta etapa não se inicia a implementação de controllers.
