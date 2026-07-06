# CI/CD — Guia didático do pipeline

Este documento explica **como o pipeline funciona**, **por que cada etapa existe** e **como executar localmente** cada ferramenta. O objetivo é servir como material de aprendizado de um fluxo profissional com ferramentas gratuitas e open source.

**Workflow:** [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)  
**Deploy real (opcional):** [`.github/workflows/deploy-production.yml`](../.github/workflows/deploy-production.yml)

---

## Visão geral do fluxo

Estratégia: **fail-fast sequencial** nos testes (barato → caro) e **bloqueio total** antes de Docker/publish. PR valida qualidade sem gastar minutos em build de imagem.

```
Commit / PR / manual
        │
        ▼
┌───────────────────┐
│ 1. Build          │  compile (falha em ~30s se código não compila)
└─────────┬─────────┘
          ▼
┌───────────────────┐
│ 2. Unit tests     │  ~15s — falha aqui aborta tudo abaixo
└─────────┬─────────┘
          ▼
┌───────────────────┐
│ 3. Smoke (Maven)  │  só se unit passou
└─────────┬─────────┘
          ▼
┌───────────────────┐
│ 4. Integration    │  Testcontainers + JaCoCo 80%
│    + Coverage     │
└─────────┬─────────┘
          ▼
┌───────────────────┐     ┌───────────────────┐
│ 5. Sonar (opt.)   │     │ 6. Trivy FS       │  só após integração OK
└─────────┬─────────┘     └─────────┬─────────┘
          └───────────┬─────────────┘
                      ▼
          (somente em push na main/develop)
┌───────────────────┐
│ 7. Docker build   │  1× build — reutilizado no publish
└─────────┬─────────┘
          ▼
┌───────────────────┐
│ 8. Trivy image    │
└─────────┬─────────┘
          ▼
┌───────────────────┐
│ 9. Deploy simulado│  compose + smoke HTTP
└─────────┬─────────┘
          ▼
┌───────────────────┐
│ 10. Publish GHCR  │  tag + push da imagem já escaneada (sem rebuild)
└─────────┬─────────┘
          ▼
┌───────────────────┐
│ Deploy produção   │  workflow separado; CI verde + DEPLOY_ENABLED
└───────────────────┘
```

### Economia de minutos

| Decisão | Por quê |
| ------- | ------- |
| Testes em **série** (unit → smoke → integration) | Se unit falha (~15s), não roda Testcontainers nem Trivy |
| Trivy FS **após** integração | Não escaneia repo com código que já falhou nos testes |
| Docker **só em push** | PR valida testes + Sonar + Trivy FS sem build de imagem (~3–5 min economizados) |
| Publish **reusa** imagem do job 7 | Evita segundo `docker build` completo |
| `concurrency: cancel-in-progress` | Novo push cancela run anterior na mesma branch |
| `timeout-minutes` em cada job | Evita runner preso consumindo minutos |

### Fail-fast e abort

Cada job declara `needs:`. Se **qualquer** dependência falha, os jobs seguintes **não iniciam**. O job `docker` exige integração + Trivy FS + Sonar (ou Sonar pulado) com sucesso.

**PR:** jobs 1–6 rodam. **Push:** pipeline completo 1–10.

### Por que o deploy falha se os testes falharem?

Cada job usa `needs:` para declarar dependências. Se **qualquer** job obrigatório falhar, os jobs seguintes **não iniciam** (economiza minutos). O `docker` exige integração + Trivy FS + Sonar OK (ou Sonar desligado). Jobs 7–10 só rodam em **push**; em PR valida-se até o job 6.

Isso evita publicar imagens quebradas ou deployar código que não passou nos gates — prática padrão em empresas de médio/grande porte.

---

## Segurança do pipeline (repositório público)

| Regra | Implementação | Por quê |
| ----- | ------------- | ------- |
| Auto-run só em `main` e `develop` | `on.push.branches` | Branches de integração conhecidas |
| PR de fork não roda o pipeline | `if: head.repo == github.repository` | Forks não podem roubar secrets via PR |
| Sem `pull_request_target` | Não usado | Evita execução com permissões elevadas em código não confiável |
| `workflow_dispatch` | Botão "Run workflow" | Execução manual sem commit |
| Secrets no GitHub | `SONAR_TOKEN`, `SSH_*` | Nunca versionar tokens |
| Deploy em Environment | `environment: production` | Permite aprovação manual futura |

### GitHub Secrets (Settings → Secrets and variables → Actions)

| Secret | Obrigatório | Uso |
| ------ | ----------- | --- |
| `SONAR_TOKEN` | Só se Sonar ativo | Token SonarCloud |
| `SSH_HOST`, `SSH_USER`, `SSH_KEY` | Só deploy VPS | Deploy produção |

### Variables

| Variable | Valor | Uso |
| -------- | ----- | --- |
| `SONAR_ENABLED` | `true` | Ativa job Sonar |
| `DEPLOY_ENABLED` | `true` | Ativa deploy VPS após CI |

### Futuro: GitHub Environments com aprovação

Em **Settings → Environments → production**, configure **Required reviewers**. O job `deploy-production` já usa `environment: production`; ao ativar revisores, ninguém faz deploy sem aprovação humana — padrão em bancos e fintechs.

---

## 1. Testes unitários

### O que roda

```bash
./mvnw test
```

Classes em `domain/service/*Test`, `api/controller/ViolationControllerTest` (`@WebMvcTest`), validadores, DTOs.

### Por que são unitários?

| Critério | Neste projeto |
| -------- | ------------- |
| Sem rede real | Mockito substitui repositórios |
| Sem banco | Nenhum `@SpringBootTest` com Postgres |
| Rápidos | Segundos, não minutos |
| Determinísticos | Mesmo resultado sempre |

### O que foi mockado

- `ViolationRepository`, `ViolationPersistenceService`, `Clock` em `ViolationServiceTest`
- `ViolationService` em `ViolationControllerTest`

### O que NÃO mockar

- Lógica sob teste (`ViolationEvaluationService` — puro Java, zero mocks)
- Validadores Jakarta quando o teste é **do próprio validador**

### Tempo esperado

~10–15 segundos no CI (com cache Maven).

### Como empresas usam

Rodam em **todo commit** como primeiro gate — feedback em menos de 1 minuto. Desenvolvedores rodam `./mvnw test` antes de cada push.

### Erros comuns de iniciantes

- Mockar a classe que está testando
- `@SpringBootTest` para tudo (vira integração lenta)
- Testar framework em vez de regra de negócio

---

## 2. Testes de integração

### O que roda

```bash
./mvnw verify -DskipTests
```

Tag JUnit `@Tag("integration")` — executado pelo **Maven Failsafe** (fase `integration-test`).

### Diferença para unitários

| | Unitário | Integração |
|---|----------|------------|
| Spring completo | Não / slice | Sim |
| Banco | Mock | Postgres real (container) |
| Flyway | Não | Sim |
| Velocidade | Rápido | Mais lento (~1–2 min) |
| Plugin Maven | Surefire | Failsafe |

### Testcontainers — vantagens

- Mesmo SGBD de produção (PostgreSQL 16)
- Sem instalar Postgres na máquina
- Descarta container ao final — estado limpo

### H2: quando usar e quando não

| H2 | PostgreSQL (Testcontainers) |
|----|----------------------------|
| Protótipo rápido | Este projeto |
| Sem Docker no CI | Flyway + tipos Postgres |
| Dialeto diferente | Constraint UNIQUE real, concorrência |

**Não usamos H2** porque o projeto usa PostgreSQL, Flyway e testes de concorrência com constraint real — H2 poderia mascarar bugs.

### Rest Assured — por que não usamos

Já temos **MockMvc** (slice) e **MockMvc + Testcontainers** (integração HTTP). Rest Assured seria redundante. Use Rest Assured quando testar APIs **externas** ou contratos em linguagem BDD (`given().when().then()`).

---

## 3. Smoke tests

Dois níveis neste projeto:

### A) Smoke Maven (`@Tag("smoke")`)

```bash
./mvnw test -Dgroups=smoke -DexcludedGroups=
```

Classe: `ApplicationSmokeTest` — contexto sobe, `/actuator/health`, um `POST /evaluate`.

**Por que é smoke?** Verifica o **mínimo** para dizer "a aplicação está viva". Não cobre todos os cenários.

**Limitações:** Não substitui integração completa nem testes de carga.

### B) Smoke HTTP pós-container (`scripts/smoke-test-ci.sh`)

Rodado no CI após `docker/ci/compose.yaml up`. Testa a **imagem Docker real**, não só o classpath Maven.

```bash
# Local (com stack rodando)
BASE_URL=http://localhost:8080 ./scripts/smoke-test-ci.sh
```

Smoke completo manual (50 casos): `./scripts/smoke-test-50.sh`

---

## 4. Coverage (JaCoCo)

### Como funciona

1. `prepare-agent` instrumenta bytecode durante testes
2. Gera `.exec` com linhas executadas
3. `report` gera HTML + **XML** (`target/site/jacoco/jacoco.xml`)
4. `check` falha o build se cobertura < threshold

### Métricas importantes

| Métrica | Significado |
| ------- | ----------- |
| **LINE** | Linhas executadas |
| **BRANCH** | Caminhos if/else cobertos |
| **INSTRUCTION** | Bytecode (mais fino) |

### Percentual recomendado

- **80%+** em regras de negócio (`domain.service`) — configurado no `pom.xml`
- 100% em todo o projeto é raramente custo-efetivo
- Foque cobertura onde há **lógica**, não em getters/setters

### Comandos locais

```bash
./mvnw verify                              # testes + relatório + gate
open target/site/jacoco/index.html         # HTML (Linux: xdg-open)
```

---

## 5. Sonar (SonarCloud)

Arquivo: [`sonar-project.properties`](../sonar-project.properties)

### Conceitos

| Conceito | O que é |
| -------- | ------- |
| **Bug** | Provável defeito em runtime |
| **Code Smell** | Manutenibilidade ruim (não quebra agora) |
| **Vulnerability** | Falha de segurança conhecida |
| **Security Hotspot** | Código sensível que precisa revisão humana |
| **Coverage** | % importado do JaCoCo XML |
| **Duplicação** | Código copiado — aumenta custo de mudança |

### Quality Gate recomendado (SonarCloud default)

- Coverage em new code ≥ 80%
- Zero bugs/vulnerabilities em new code
- Duplicação em new code ≤ 3%
- Maintainability rating A em new code

### Configurar SonarCloud (uma vez)

1. [sonarcloud.io](https://sonarcloud.io) → importar repositório GitHub
2. Ajustar `sonar.organization` e `sonar.projectKey` em `sonar-project.properties`
3. GitHub → Secret `SONAR_TOKEN`
4. GitHub → Variable `SONAR_ENABLED=true`
5. **Desativar Automatic Analysis** (obrigatório para análise via CI com cobertura JaCoCo):
   - [Analysis Method do projeto](https://sonarcloud.io/project/configuration/analysis_method?id=oswaldoschermach_speed-violation-service)
   - **Administration** → **Analysis Method** → desligar **Automatic Analysis**
   - O pipeline também tenta desativar via API antes do scan (idempotente)

### Erro: "CI analysis while Automatic Analysis is enabled"

SonarCloud não permite **duas fontes de análise** no mesmo projeto. Como o pipeline envia cobertura JaCoCo pelo Maven, a Automatic Analysis precisa estar **OFF**.

| Solução | Quando usar |
| ------- | ----------- |
| UI: Analysis Method → OFF | Correção imediata (1 clique, sem novo deploy) |
| Re-run do job `sonar` após desligar | Valida sem commit |
| Step `Disable Automatic Analysis` no CI | Automático em cada run (usa `SONAR_TOKEN`) |

**Por que `sonar-project.properties` não basta?** Projetos importados pelo GitHub App já podem ter Automatic Analysis ligada; o ficheiro impede auto-scan em projetos novos, mas não desliga o que já estava ativo.

### Comando local

```bash
export SONAR_TOKEN=seu_token
./mvnw verify sonar:sonar -Dsonar.token=$SONAR_TOKEN
```

---

## 6. Trivy

### CVE e severidades

- **CVE** — identificador público de vulnerabilidade (ex.: CVE-2024-XXXX)
- **CRITICAL / HIGH** — pipeline falha (`exit-code: 1`)
- **MEDIUM / LOW** — reportados, não bloqueiam (ajustável)

### Filesystem vs Image scan

| Tipo | O que analisa |
| ---- | ------------- |
| **filesystem** | `pom.xml`, dependências, arquivos do repo |
| **image** | Camadas da imagem Docker (OS Alpine + JAR + pacotes) |

### Comandos locais

```bash
# Instalar: https://aquasecurity.github.io/trivy/
trivy fs --severity CRITICAL,HIGH .
docker build -t speed-violation-service:local -f docker/prod/Dockerfile .
trivy image --severity CRITICAL,HIGH speed-violation-service:local
```

---

## 7. Docker

Arquivo: [`docker/prod/Dockerfile`](../docker/prod/Dockerfile)

| Prática | Implementação |
| ------- | ------------- |
| Multi-stage | Stage `build` (JDK) + stage runtime (JRE) |
| Imagem enxuta | `eclipse-temurin:21-jre-alpine` (~metade do JDK) |
| Non-root | `USER spring:spring` |
| Healthcheck | `curl` readiness actuator |
| Cache Maven | `dependency:go-offline` antes de copiar `src/` |
| JVM em container | `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` |

**Por que `-DskipTests` no build da imagem?** Testes já rodaram no CI. A imagem empacota artefato validado — build rápido e determinístico.

Stack CI (sem Caddy): [`docker/ci/compose.yaml`](../docker/ci/compose.yaml)

---

## 8. Jobs do GitHub Actions

| # | Job | Objetivo | Falha quando | Depende de |
|---|-----|----------|--------------|------------|
| 1 | `build` | Compilar | Erro de compilação | — |
| 2 | `unit-tests` | Testes rápidos | Assertion/mock falha | build |
| 3 | `smoke-tests` | Saúde mínima Maven | Health/evaluate falha | unit-tests |
| 4 | `integration-tests` | E2E + JaCoCo gate | Integração ou coverage < 80% | smoke-tests |
| 5 | `sonar` | Análise estática | Quality Gate Sonar | integration |
| 6 | `trivy-filesystem` | CVE no repo | CRITICAL/HIGH | integration |
| 7 | `docker` | Build imagem (push only) | Dockerfile inválido | integration + trivy-fs + sonar* |
| 8 | `trivy-image` | CVE na imagem | CRITICAL/HIGH | docker |
| 9 | `deploy-simulated` | Compose + smoke HTTP | Container ou API down | docker + trivy-image |
| 10 | `publish-image` | Push GHCR | Build/push falha | deploy-simulated |

**Cache Maven:** `actions/setup-java` com `cache: maven` restaura `~/.m2` entre jobs.

---

## 9. Deploy simulado vs produção

| | CI simulado | Produção (opcional) |
|---|-------------|---------------------|
| Workflow | `ci.yml` job 9 | `deploy-production.yml` |
| Onde | Runner GitHub | VPS via SSH |
| TLS | Não | Caddy + Let's Encrypt |
| Quando | Todo CI | CI passou na `main` + `DEPLOY_ENABLED` |

---

## Comandos rápidos (cheat sheet)

```bash
# Compilar
./mvnw compile test-compile

# Só unitários
./mvnw test

# Só smoke (Maven)
./mvnw test -Dgroups=smoke -DexcludedGroups=

# Só integração + coverage gate
./mvnw verify -DskipTests

# Tudo (local, igual ao gate principal)
./mvnw verify

# Cobertura HTML
./mvnw verify && xdg-open target/site/jacoco/index.html

# Smoke HTTP (app rodando)
BASE_URL=http://localhost:8080 ./scripts/smoke-test-ci.sh

# Stack local completa
docker compose -f docker/local/compose.yaml up -d --build

# Deploy simulado local (imagem + CI compose)
docker build -t speed-violation-service:ci -f docker/prod/Dockerfile .
APP_IMAGE=speed-violation-service:ci docker compose -f docker/ci/compose.yaml up -d --wait
BASE_URL=http://localhost:8080 ./scripts/smoke-test-ci.sh
docker compose -f docker/ci/compose.yaml down -v
```

---

## Ferramentas do enunciado — checklist

| Ferramenta | Usada? | Motivo |
| ---------- | ------ | ------ |
| JUnit 5 | Sim | Padrão Spring Boot |
| Mockito | Sim | Unitários de serviço/controller |
| Spring Boot Test | Sim | MockMvc, `@SpringBootTest` |
| Testcontainers | Sim | Postgres real |
| Rest Assured | Não | MockMvc cobre HTTP interno |
| JaCoCo | Sim | Coverage + gate + Sonar |
| SonarCloud | Opcional | Requer `SONAR_TOKEN` |
| Trivy | Sim | FS + image no CI |
| Docker | Sim | Multi-stage + deploy simulado |
| GitHub Actions | Sim | Pipeline completo |
| Actuator | Sim | Health/readiness smoke |

---

## Problemas que este pipeline resolve

- **"Funciona na minha máquina"** — integração com Postgres real
- **Regressão silenciosa** — unitários + gate de cobertura
- **Imagem vulnerável** — Trivy antes do publish
- **Deploy de código quebrado** — cadeia `needs:` bloqueia publish/deploy
- **Dívida técnica invisível** — Sonar (quando habilitado)

## Erros comuns em times iniciantes

- Um único job `mvn verify` sem separação — debug lento
- Publicar imagem antes de testar container
- Secrets no repositório
- `pull_request_target` em repo público
- 100% coverage como meta em DTOs e configs
