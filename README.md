# speed-violation-service

Microserviço REST para apuração de infrações por excesso de velocidade (prova prática Velsis).

> **Avaliador / recrutador:** comece pelo [Guia rápido](#guia-rápido-recrutador) ou pela seção [Entrega](#entrega). Um comando sobe app + banco; teste via Swagger, Postman ou curl.

## Tecnologias

- Java 21
- Spring Boot 3.x
- PostgreSQL + Flyway
- Maven
- Springdoc OpenAPI (Swagger)
- Collection Postman (`docs/postman/`)

## Pré-requisitos

**Opção A (Docker — recomendada para avaliação):**

- Docker e Docker Compose

**Opção B (JVM local) ou desenvolvimento:**

- Java 21
- Docker e Docker Compose (apenas para o Postgres)
- Maven Wrapper incluído (`./mvnw`)

## Perfis Spring

| Perfil | Uso | Arquivo |
| ------ | --- | ------- |
| `local` (padrão) | Desenvolvimento na máquina ou stack `docker/local` | `application-local.yml` |
| `prod` | Deploy remoto (PaaS ou `docker/prod`) | `application-prod.yml` |
| `test` | Testes automatizados | `application-test.yml` |

O perfil ativo é definido por `SPRING_PROFILES_ACTIVE` (padrão: `local`).

## Guia rápido (recrutador)

**Pré-requisito:** Docker e Docker Compose instalados. Java **não** é necessário se usar a Opção A.

### 1. Subir tudo com um comando

Na raiz do repositório:

```bash
docker compose -f docker/local/compose.yaml up -d --build
```

Na primeira execução o build pode levar alguns minutos (download de dependências Maven). Nas seguintes, sobe em segundos.

Aguarde a aplicação ficar pronta (cerca de 1–2 min na primeira vez):

```bash
# Repita até retornar {"status":"UP"} ou status 200
curl -s http://localhost:8080/actuator/health
```

Verificar containers:

```bash
docker ps --filter name=speed-violation
```

Esperado: `speed-violation-postgres` e `speed-violation-app` com status `healthy` (ou `Up`).

### 2. Testar a API

| Recurso | URL |
| ------- | --- |
| Swagger UI (recomendado) | http://localhost:8080/swagger-ui.html |
| Health check | http://localhost:8080/actuator/health |
| OpenAPI JSON | http://localhost:8080/api-docs |

**Postman / Insomnia**

1. Importe `docs/postman/speed-violation-service.postman_collection.json`
2. Importe o environment `docs/postman/local.postman_environment.json`
3. Selecione o environment **Speed Violation — Local**
4. Execute na ordem sugerida na pasta *Infrações* (comece por *Com infração*)

Para ambiente hospedado, altere `baseUrl` no environment para a URL pública.

**Teste mínimo via curl**

```bash
curl -s -X POST http://localhost:8080/api/v1/violations/evaluate \
  -H "Content-Type: application/json" \
  -H "x-origin: FIXED" \
  -d '{
    "licensePlate": "ABC1D23",
    "measuredSpeed": 92,
    "speedLimit": 60,
    "equipmentId": "RAD-CWB-001",
    "captureTimestamp": "2026-06-08T14:30:00Z"
  }'
```

Consultar infrações da mesma placa:

```bash
curl -s "http://localhost:8080/api/v1/violations?licensePlate=ABC1D23"
```

### 3. Smoke test automatizado (50 casos)

Com a stack no ar e `curl` + `jq` instalados:

```bash
./scripts/smoke-test-50.sh
```

O script valida cenários de infração, sem infração, validações 400, duplicata 409 e consulta por placa.

### 4. Parar e limpar

```bash
docker compose -f docker/local/compose.yaml down
```

Para remover também o volume do banco (dados apagados):

```bash
docker compose -f docker/local/compose.yaml down -v
```

### Problemas comuns

**Conflito de nome do container Postgres** (se você já rodou uma versão anterior do projeto):

```bash
docker rm -f speed-violation-postgres
docker compose -f docker/local/compose.yaml up -d --build
```

**Porta 8080 ou 5433 já em uso:** copie e edite as variáveis de porta:

```bash
cp docker/local/.env.example docker/local/.env
# Ajuste SERVER_PORT e/ou POSTGRES_PORT em docker/local/.env
docker compose -f docker/local/compose.yaml --env-file docker/local/.env up -d --build
```

---

## Entrega

Informações para avaliação da prova prática Velsis.

### Links

| Item | URL |
| ---- | --- |
| Repositório Git (público) | https://github.com/oswaldoschermach/speed-violation-service |
| API hospedada | _Pendente — deploy previsto em VPS Oracle Cloud_ |
| Swagger (local) | http://localhost:8080/swagger-ui.html |

> Quando a API estiver no ar, atualize a linha **API hospedada** com a URL pública (ex.: `http://<IP>:8080` ou domínio com HTTPS).

### Passos de demo sugeridos

1. **Subir o ambiente** — [Guia rápido](#guia-rápido-recrutador), passo 1 (`docker compose -f docker/local/compose.yaml up -d --build`)
2. **Conferir saúde** — `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
3. **Apurar infração** — Swagger, Postman (*Com infração*) ou curl do guia rápido → `hasViolation: true`, gravidade `SERIOUS`
4. **Consultar placa** — `GET /api/v1/violations?licensePlate=ABC1D23` → lista com o registro
5. **Sem infração** — Postman *Sem infração* → `hasViolation: false`; consulta não ganha novo item
6. **Validação** — Postman *Placa inválida* → 400; *Captura duplicada* → 409 (após passo 3)
7. **(Opcional)** `./scripts/smoke-test-50.sh` — 50 cenários automatizados

### Collection Postman

Arquivos em `docs/postman/`:

```
docs/postman/
├── speed-violation-service.postman_collection.json
└── local.postman_environment.json
```

Cenários incluídos (alinhados ao Swagger):

| Request | Resultado esperado |
| ------- | ------------------ |
| Com infração (exemplo da prova) | 200, `SERIOUS`, `41.67%` |
| Sem infração | 200, `hasViolation: false` |
| Placa formato antigo | 200, placa `ABC1234` aceita |
| Via acima de 100 km/h | 200, tolerância percentual |
| Placa inválida | 400 `INVALID_LICENSE_PLATE` |
| Header x-origin ausente | 400 `INVALID_ORIGIN` |
| Captura duplicada | 409 `DUPLICATE_VIOLATION` |
| Consultar por placa | 200, lista de infrações |
| Actuator health | 200, `status: UP` |

### Suposições e decisões assumidas

| Tópico | Decisão |
| ------ | ------- |
| **Persistência** | PostgreSQL + Flyway em vez de memória (enunciado aceita memória). Justificativa: durabilidade, concorrência real e índices. Ver [decisão técnica](#persistência-postgresql-em-vez-de-memória). |
| **Duplicata 409** | Não exigido no PDF. Constraint única `(placa, equipamento, captureTimestamp)` evita reprocessamento; reenvio retorna `DUPLICATE_VIOLATION`. |
| **Tolerância percentual** | Truncamento no **resultado final**: `trunc(measuredSpeed × 0,93)`, não na margem antes de subtrair. Coberto por teste. |
| **Limite 100 km/h** | Até 100 km/h inclusive → margem em km/h; acima de 100 → margem percentual. |
| **Fronteiras de gravidade** | 20% → `MEDIUM`; acima de 20% até 50% → `SERIOUS`; acima de 50% → `VERY_SERIOUS`. |
| **Mensagens de erro** | Em inglês, conforme contrato do PDF (`INVALID_LICENSE_PLATE`, etc.). |
| **Deploy** | Perfil `prod` + `docker/prod/` para VPS Oracle; URL pública será adicionada nesta seção após o deploy. |

### Instruções especiais

- **Avaliação sem Java:** use apenas Docker (Opção A do guia rápido).
- **Primeiro build Docker:** pode levar ~3–5 min (cache Maven na imagem).
- **OpenAPI ao vivo:** com a app rodando, `GET /api-docs` retorna o spec atualizado (mesma base da collection Postman).

---

## Execução local (detalhes)

### Opção A — Stack completa em Docker (recomendado para demo)

Equivalente ao [guia rápido](#guia-rápido-recrutador) acima:

```bash
docker compose -f docker/local/compose.yaml up -d --build
```

API: `http://localhost:8080`  
Health: `http://localhost:8080/actuator/health`  
Swagger UI: `http://localhost:8080/swagger-ui.html`

Parar: `docker compose -f docker/local/compose.yaml down`

### Opção B — App na JVM + Postgres em Docker

Requer Java 21 instalado.

```bash
# 1. Só o banco (perfil local)
docker compose -f docker/local/compose.yaml up -d postgres

# 2. Aplicação (perfil local é o padrão)
./mvnw spring-boot:run
```

### Estrutura Docker

```
docker/
├── local/
│   ├── compose.yaml      # app + Postgres para desenvolvimento
│   └── .env.example
└── prod/
    ├── Dockerfile        # imagem de produção (usada também no build local)
    ├── compose.yaml      # stack self-hosted (VPS)
    └── .env.example
```

**Deploy remoto (Railway, Render, Fly.io):** aponte o build para `docker/prod/Dockerfile`, defina `SPRING_PROFILES_ACTIVE=prod` e as variáveis `DB_*` do Postgres gerenciado (ver `docker/prod/.env.example`).

OpenAPI JSON: `http://localhost:8080/api-docs`

### Swagger / OpenAPI (teste interativo)

A documentação OpenAPI está completa com **exemplos prontos** para o recrutador testar sem montar JSON manualmente:

1. Acesse `http://localhost:8080/swagger-ui.html`
2. Expanda **Infrações** → **POST /api/v1/violations/evaluate**
3. Clique em **Try it out**
4. Selecione um exemplo no dropdown do body (ex.: *Com infração (exemplo da prova)*)
5. Informe o header `x-origin` (ex.: `FIXED`) e execute
6. Consulte a placa em **GET /api/v1/violations** com o mesmo valor

Exemplos disponíveis no Swagger:

| Exemplo | Resultado esperado |
| -------- | ------------------ |
| Com infração (exemplo da prova) | `hasViolation: true`, gravidade `SERIOUS` |
| Sem infração (dentro da tolerância) | `hasViolation: false`, `violation: null` |
| Placa formato antigo | Placa `ABC1234` aceita |
| Via acima de 100 km/h | Tolerância percentual truncada |
| Placa inválida (gera 400) | Erro `INVALID_LICENSE_PLATE` |


### Banco de dados (dev)

| Campo    | Valor             |
| -------- | ----------------- |
| Host     | `localhost`       |
| Porta    | `5433`            |
| Database | `speed_violation` |
| Usuário  | `speed_violation` |
| Senha    | `speed_violation` |

JDBC: `jdbc:postgresql://localhost:5433/speed_violation`

### Dados de teste

```bash
docker exec -i speed-violation-postgres psql -U speed_violation -d speed_violation -c "TRUNCATE violations;"
docker exec -i speed-violation-postgres psql -U speed_violation -d speed_violation < scripts/seed-violations.sql
```

## Exemplos de uso (curl)

### Com infração (200)

```bash
curl -s -X POST http://localhost:8080/api/v1/violations/evaluate \
  -H "Content-Type: application/json" \
  -H "x-origin: FIXED" \
  -d '{
    "licensePlate": "ABC1D23",
    "measuredSpeed": 92,
    "speedLimit": 60,
    "equipmentId": "RAD-CWB-001",
    "captureTimestamp": "2026-06-08T14:30:00Z"
  }' | jq
```

Resposta esperada: `hasViolation: true`, `consideredSpeed: 85`, `excessPercentage: 41.67`, gravidade `SERIOUS`.

### Sem infração (200)

```bash
curl -s -X POST http://localhost:8080/api/v1/violations/evaluate \
  -H "Content-Type: application/json" \
  -H "x-origin: MOBILE" \
  -d '{
    "licensePlate": "ABC1D23",
    "measuredSpeed": 64,
    "speedLimit": 60,
    "equipmentId": "RAD-CWB-001",
    "captureTimestamp": "2026-06-08T14:30:00Z"
  }' | jq
```

Resposta esperada: `hasViolation: false`, `violation: null`, `excessPercentage: 0.0`.

### Erro de validação (400)

```bash
curl -s -X POST http://localhost:8080/api/v1/violations/evaluate \
  -H "Content-Type: application/json" \
  -H "x-origin: FIXED" \
  -d '{
    "licensePlate": "INVALID",
    "measuredSpeed": 92,
    "speedLimit": 60,
    "equipmentId": "RAD-CWB-001",
    "captureTimestamp": "2026-06-08T14:30:00Z"
  }' | jq
```

Resposta esperada:

```json
{
  "error": "INVALID_LICENSE_PLATE",
  "message": "Invalid license plate format",
  "timestamp": "..."
}
```

### Consulta por placa (GET)

```bash
curl -s "http://localhost:8080/api/v1/violations?licensePlate=ABC1D23" | jq
```

Retorna lista de infrações persistidas para a placa (vazia se não houver).

## Testes

```bash
# Testes rápidos (unitários + slice de web)
./mvnw test

# Suite completa + relatório e enforcement de cobertura (JaCoCo)
./mvnw verify
```

> Os testes de integração e o `verify` usam Testcontainers e exigem Docker em execução.

Cobertura principal:

- **Regras de apuração** (`ViolationEvaluationServiceTest`) — tolerância, percentual, classificação CTB, fronteiras 20%/50% e truncagem percentual
- **Orquestração** (`ViolationServiceTest`) — persistência condicional e montagem da resposta
- **Validação** (`LicensePlateValidatorTest`, `EvaluateViolationRequestTest`)
- **API — slice** (`ViolationControllerTest`) — MockMvc dos endpoints e erros 400
- **API — integração** (`ViolationApiIntegrationTest`) — fluxo completo `POST /evaluate` → persistência → `GET ?licensePlate=` com Postgres real
- **API — validação HTTP** (`ViolationApiValidationIntegrationTest`) — 400 de placa, `x-origin` e `captureTimestamp` com stack completa
- **API — concorrência HTTP** (`ViolationApiConcurrencyIntegrationTest`) — 16 threads via MockMvc: 1× 200 + 15× 409, um único registro (RF4)
- **Concorrência de serviço** (`ViolationServiceConcurrencyTest`) — mesma captura em paralelo na camada de domínio
- **Contexto Spring** (`SpeedViolationServiceApplicationTests`) — Testcontainers + Flyway

### Cobertura (JaCoCo)

`./mvnw verify` gera o relatório em `target/site/jacoco/index.html` e **falha o build** se a camada de regras de negócio (`domain.service`) ficar abaixo de **80%** de cobertura de linha e branch (RNF3).

## Arquitetura

```
api/
├── controller/     → HTTP (delega ao service)
├── request/        → DTOs de entrada + Bean Validation
├── response/       → DTOs de saída
├── validation/     → @ValidLicensePlate (regex compiladas)
├── exception/      → handler HTTP, ErrorCode e ApiErrorResponse
└── mapper/         → Request → Command, Entity → Response

domain/
├── model/          → enums, EvaluateViolationCommand
├── exception/      → exceções de regra de negócio (ex.: captura duplicada)
└── service/
    ├── ViolationEvaluationService  → regras puras (sem Spring/JPA)
    └── ViolationService            → orquestra apuração + persistência

persistence/
├── entity/         → Violation (JPA)
└── repository/     → ViolationRepository

config/             → ToleranceProperties, beans de domínio
```

Fluxo do `POST /evaluate`:

1. Controller valida request (`@Valid`) e header `x-origin`
2. `ViolationMapper` converte para `EvaluateViolationCommand`
3. `ViolationService.evaluate()` orquestra cálculo, persistência (se houver infração) e resposta
4. `ViolationEvaluationService` concentra tolerância, excesso e gravidade

## Decisões técnicas

### Persistência: PostgreSQL em vez de memória

O enunciado aceita armazenamento **em memória**. Optamos por **PostgreSQL + Flyway + JPA** porque:

- Persistência sobrevive a reinícios da aplicação (útil para demo e testes manuais)
- O acesso concorrente (RF4) fica a cargo do banco, com garantias transacionais reais em vez de uma estrutura em memória sincronizada manualmente
- Flyway garante schema versionado; Hibernate apenas valida (`ddl-auto: validate`)
- Índices por `license_plate` (e `license_plate, processed_at`) tornam a consulta por placa eficiente mesmo com volume
- Constraint única `(license_plate, equipment_id, capture_timestamp)` impede duplicatas; reenvios retornam **409** (`DUPLICATE_VIOLATION`)

**Trade-off assumido:** rodar/hospedar exige um Postgres disponível (via `docker/local` ou banco gerenciado no deploy). Para o escopo desta prova consideramos que a robustez de persistência e concorrência compensa a dependência extra. Uma estrutura em memória (`ConcurrentHashMap`) seria suficiente e removeria a dependência, ao custo de perder durabilidade.

A API exposta (`POST /evaluate`, `GET ?licensePlate=`) permanece igual ao contrato da prova.

### Identificador `UUID` na entidade `Violation`

A tabela `violations` usa `UUID` como chave primária (`gen_random_uuid()` no Postgres + `@GeneratedValue(strategy = GenerationType.UUID)` no JPA).

**Por quê?**

- **Microserviço:** UUID evita colisão de IDs em cenários distribuídos
- **Identificador opaco:** não expõe sequência previsível
- **API:** o contrato da prova **não expõe** o `id` na resposta; consulta principal é por `license_plate`

**Alternativa considerada:** `Long` com `BIGSERIAL` seria mais simples para escopo isolado.

### Lombok e `equals`/`hashCode` na entidade `Violation`

O projeto usa Lombok de forma consistente na camada de aplicação (`@RequiredArgsConstructor`, `@Slf4j`, `@Getter` em exceções) e na entidade JPA (`@Getter`, `@Builder`, `@NonNull` no construtor do builder). **`equals` e `hashCode` de `Violation` permanecem manuais.**

**Por quê?**

Entidades JPA com `@GeneratedValue` costumam existir **sem `id`** antes do `save` (estado transient). O padrão idiomático de igualdade por identidade persistida é:

- **`equals`:** só retorna `true` quando `id != null` e os IDs coincidem (referência `==` continua valendo)
- **`hashCode`:** constante por classe (`getClass().hashCode()`), estável enquanto `id` ainda é `null`

O `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` do Lombok, restrito ao campo `id`, trataria **duas instâncias distintas com `id == null` como iguais**. Isso não afeta o fluxo atual (`Violation` é construída e persistida diretamente, sem entrar em `Set`/`Map` antes do save), mas pode causar deduplicação incorreta se alguém no futuro agrupar entidades transientes em coleções com igualdade.

**Decisão:** manter o par manual documenta a intenção JPA e blinda esse edge case com custo mínimo (~15 linhas). O restante do boilerplate continua delegado ao Lombok ou a records (DTOs, commands, config).

### Schema via Flyway (não via JPA)

O DDL vive em `db/migration/`; o Hibernate usa `ddl-auto: validate` apenas para conferir alinhamento entidade ↔ banco.

### Separação `ViolationEvaluationService` / `ViolationService`

- **Evaluation:** regras de cálculo puras, testáveis sem Spring
- **Violation:** orquestração, transação, persistência e DTO de resposta

Controllers ficam finos — recebem HTTP e delegam.

### Por que arquitetura em camadas (e não Clean ou Hexagonal)

**O que foi adotado:** organização em camadas (`api` → `domain` → `persistence`) com separação explícita entre regra de cálculo (`ViolationEvaluationService`), orquestração (`ViolationService`) e infraestrutura (JPA, HTTP).

**Clean Architecture (completa) — não adotada**

Clean Architecture completa exigiria, entre outros elementos:

- Entidades de domínio distintas das entidades JPA
- Use cases explícitos por fluxo
- Inversão total de dependências via interfaces em todas as fronteiras

Para um microserviço com **dois endpoints** e regras **fixas no enunciado**, isso geraria boilerplate (modelos duplicados, mappers extras, ports/adapters) sem ganho proporcional. O RNF1 pede organização e **justificativa**, não um padrão específico.

Ainda assim, aplicamos **princípios** úteis do Clean: regras de negócio isoladas do framework, DTOs na borda HTTP, controller fino e testes unitários diretos na camada de cálculo.

**Arquitetura Hexagonal — não adotada**

Hexagonal (Ports & Adapters) faria sentido se houvesse **múltiplos adapters de entrada** (REST + fila + batch) ou **troca frequente de saída** (Postgres hoje, outro store amanhã), com o núcleo desacoplado via ports explícitos (`ViolationRepositoryPort`, adapters de infraestrutura, etc.).

Neste escopo:

- Entrada única: REST
- Saída única: PostgreSQL via Spring Data JPA
- Sem integrações externas além do banco
- Prazo e escopo de prova prática (qualidade > quantidade de camadas)

Introduzir ports/adapters aqui seria **over-engineering**: mais interfaces e classes de glue para o mesmo comportamento, dificultando a leitura para quem revisa o repositório.

**Resumo:** camadas simples + domínio de cálculo puro entregam clareza, testabilidade e manutenção adequadas ao tamanho real do serviço. Clean e Hexagonal são valiosos em sistemas maiores e evolutivos; neste projeto, a complexidade extra não se paga.

### Truncagem da tolerância percentual (limite > 100 km/h)

O RF3 define, para vias acima de 100 km/h, `consideredSpeed = velocidade medida − 7% (truncar para inteiro)`.

**Suposição adotada (leitura literal):** o truncamento é aplicado ao **resultado final** da velocidade considerada, não à margem:

```
consideredSpeed = trunc(measuredSpeed × (100 − percentMargin) / 100)
```

Exemplo: `measuredSpeed = 120`, margem 7% → `trunc(120 × 0.93) = trunc(111.6) = 111`.

Alternativa possível seria truncar a margem antes de subtrair (`120 − trunc(8.4) = 112`); optamos pela leitura literal do enunciado, coberta por teste explícito.

### Tratamento de erros e logs

- Erros de validação retornam 400 com código estável (`INVALID_LICENSE_PLATE`, `INVALID_ORIGIN`, `INVALID_CAPTURE_TIMESTAMP`, ...) e são logados em nível `warn` com contexto de domínio (placa, equipamento, campo, código).
- Erros inesperados retornam 500 com código `INTERNAL_ERROR` e mensagem genérica (`Internal server error`), logados em nível `error` com stack trace apenas no servidor — nunca exposto ao cliente (RF5).
- Toda infração persistida gera um log de auditoria em nível `info` com contexto de domínio (placa, equipamento, gravidade, código CTB, percentual de excesso e timestamps). Isso separa o caminho de sucesso (`info`) dos caminhos de erro (`warn`/`error`), adequado a um domínio de fiscalização.

### Validação de `captureTimestamp` no futuro (via `Clock` injetado)

A checagem "captureTimestamp não pode estar no futuro" é feita na camada de domínio (`ViolationService`) comparando com o `Clock` injetado, em vez de `@PastOrPresent` (que usaria o relógio do sistema, fora do controle da aplicação).

**Por quê?**

- **Fonte única de tempo:** o mesmo `Clock` bean que define `processedAt` também define o "agora" da validação temporal — sem divergência entre relógios.
- **Testabilidade:** a regra fica coberta por teste unitário com `Clock.fixed` (`ViolationServiceTest`), sem depender do relógio real.

As demais validações de `captureTimestamp` (obrigatório e formato ISO-8601) permanecem declarativas via Bean Validation/Jackson. O contrato HTTP é idêntico: 400 com `INVALID_CAPTURE_TIMESTAMP` e mensagem `Capture timestamp cannot be in the future`.

**Tolerância de clock skew (produção):** o `captureTimestamp` vem do relógio do *equipamento* (radar), não do servidor. Um pequeno adiantamento do relógio do equipamento (drift de NTP, latência de envio) faria uma captura legítima ser rejeitada como "futuro". Por isso a comparação usa uma tolerância configurável:

```
consideredFuture = captureTimestamp > (now + speed-violation.capture.future-tolerance)
```

Configuração externalizada (padrão `5s`, env `CAPTURE_FUTURE_TOLERANCE`):

```yaml
speed-violation:
  capture:
    future-tolerance: 5s   # aceita skew de até 5s; futuro real ainda é 400
```

Valores negativos ou ausentes são normalizados para `PT0S` (checagem estrita). Coberto por testes de fronteira (dentro da tolerância → aceita; além → 400).

### Mensagens de erro em inglês

Alinhadas ao contrato do PDF (`INVALID_LICENSE_PLATE`, `Invalid license plate format`, etc.), definidas nas anotações de validação e no `GlobalExceptionHandler`.
