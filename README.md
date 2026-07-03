# speed-violation-service

Microserviço REST para apuração de infrações por excesso de velocidade (prova prática Velsis).

## Tecnologias

- Java 21
- Spring Boot 3.x
- PostgreSQL + Flyway
- Maven
- Springdoc OpenAPI (Swagger)

## Pré-requisitos

- Java 21
- Docker (para PostgreSQL local)
- Maven Wrapper incluído (`./mvnw`)

## Execução local

```bash
# 1. Subir o banco
docker compose up -d

# 2. Rodar a aplicação (Java 21)
./mvnw spring-boot:run
```

API: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`  
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

**Trade-off assumido:** rodar/hospedar exige um Postgres disponível (via `docker compose`). Para o escopo desta prova consideramos que a robustez de persistência e concorrência compensa a dependência extra. Uma estrutura em memória (`ConcurrentHashMap`) seria suficiente e removeria a dependência, ao custo de perder durabilidade.

A API exposta (`POST /evaluate`, `GET ?licensePlate=`) permanece igual ao contrato da prova.

### Identificador `UUID` na entidade `Violation`

A tabela `violations` usa `UUID` como chave primária (`gen_random_uuid()` no Postgres + `@GeneratedValue(strategy = GenerationType.UUID)` no JPA).

**Por quê?**

- **Microserviço:** UUID evita colisão de IDs em cenários distribuídos
- **Identificador opaco:** não expõe sequência previsível
- **API:** o contrato da prova **não expõe** o `id` na resposta; consulta principal é por `license_plate`

**Alternativa considerada:** `Long` com `BIGSERIAL` seria mais simples para escopo isolado.

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

### Mensagens de erro em inglês

Alinhadas ao contrato do PDF (`INVALID_LICENSE_PLATE`, `Invalid license plate format`, etc.), definidas nas anotações de validação e no `GlobalExceptionHandler`.
