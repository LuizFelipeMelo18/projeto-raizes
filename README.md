# Rede Raízes do Nordeste — Solução de Back-end (Projeto Multidisciplinar 2026)

Repositório do projeto prático da trilha **Back-End** (Rede de lanchonetes **"Raízes do Nordeste"**), implementado em **Java 21**, **Spring Boot 4.1.2**, **Maven 3.9.16 (Wrapper)**, **PostgreSQL 19 local**, **Spring Data JPA**, **Flyway** e **Spring Security (JWT HS256 + BCrypt)**, sem uso de Docker ou Testcontainers.

## Estrutura do projeto

| Caminho | Conteúdo |
|---|---|
| `backend/` | Aplicação Spring Boot executável, migrations Flyway `V1`..`V11`, seed `R__demo_seed.sql`, OpenAPI 3.0.3, coleção Postman e 35 testes automatizados JUnit. |
| `backend/README.md` | **Guia passo a passo de execução** (requisitos, PostgreSQL local, `.env.example`, compilação, inicialização, Swagger e execução dos testes JUnit e Postman). |
| `backend/postman/raizes-fluxo-a.postman_collection.json` | Coleção Postman v2.1 organizada em 7 pastas com 38 requisições executáveis (`T01`..`T18`). |
| `etapa-01/` | Estado inicial, decisões e matriz de rastreabilidade (`55` requisitos rastreados). |
| `etapa-02/` | Modelagem de domínio, DER, classes, sequência, governança LGPD, promoções conceituais e contratos de API. |
| `etapa-03/` a `etapa-11/` | Relatórios técnicos de validação de cada etapa, plano de testes (`etapa-10/plano-de-testes.md`) e diagramas exportados (`etapa-11/diagramas/`). |
| `RU_Projeto_Back_End.PDF` | Documento acadêmico único em formato ABNT (renomear `RU` para o Registro Único do aluno antes da submissão no AVA). |

## Execução rápida

Consulte o passo a passo completo em [`backend/README.md`](backend/README.md):

1. Inicie o PostgreSQL 19 local na porta `5432` com os bancos `raizes` e `raizes_test`.
2. Em `backend/`, copie `.env.example` para `.env`, defina a senha do banco e `JWT_SECRET_BASE64` (32 bytes em Base64) e exporte as variáveis no terminal PowerShell.
3. Execute `.\mvnw.cmd clean verify` (perfil `test` contra `raizes_test`: **35 testes, 0 falhas**).
4. Inicie a API com `.\mvnw.cmd spring-boot:run` (ou `java -jar target\raizes-backend-0.1.0-SNAPSHOT.jar`), acesse `http://localhost:8080/swagger-ui.html` (`http://localhost:8080/openapi.yaml`) e execute a coleção `backend/postman/raizes-fluxo-a.postman_collection.json`.
