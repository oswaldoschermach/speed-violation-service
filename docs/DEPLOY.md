# Deploy em VPS com domínio (Docker + Caddy HTTPS + CI/CD)

Guia para publicar o `speed-violation-service` em um VPS com domínio real e
HTTPS automático (Let's Encrypt via Caddy). A imagem é buildada pelo **GitHub
Actions** e publicada no **GHCR**; o VPS apenas **puxa a imagem e sobe a stack**
(app + Postgres + Caddy) — sem compilar nada no servidor.

## Como funciona (CI/CD)

```
push na main ─► GitHub Actions ─► mvnw verify (testes) ─► build imagem ─► push GHCR
                                                                              │
                       VPS ◄── docker compose pull + up -d ───────────────────┘
```

- **CI** (`.github/workflows/ci.yml`): roda `./mvnw verify` em todo push/PR na `main`.
- **CD**: em push na `main` (ou tag `v*`), builda a imagem e publica em
  `ghcr.io/oswaldoschermach/speed-violation-service`.
- **VPS**: só precisa de `docker/prod/compose.yaml`, `docker/prod/Caddyfile` e um `.env`.

## Arquitetura em produção

```
Internet ──HTTPS(443)──► Caddy ──HTTP(8080, rede interna)──► app ──► Postgres
                         (TLS + Let's Encrypt)                       (volume)
```

- **Caddy** termina TLS, obtém/renova o certificado e faz proxy para a app.
- **app** só é exposta em `127.0.0.1:8080` do host (debug); não é acessível pela internet.
- **Postgres** só na rede interna do compose; dados em volume persistente.

## Pré-requisitos

- VPS Linux com Docker e Docker Compose (`docker --version`, `docker compose version`).
- Domínio gerenciado na Hostinger (aqui: `nebulax.com.br`).
- Portas **80** e **443** liberadas no firewall do VPS.
- Imagem GHCR **pública** (ver Passo 0) — ou login no GHCR no VPS.

## Passo 0 — Tornar a imagem do GHCR pública (uma vez)

Após o primeiro push na `main` (que dispara o CI e publica a imagem):

1. GitHub → repositório → aba **Packages** → `speed-violation-service`.
2. **Package settings** → **Danger Zone** → **Change visibility** → **Public**.

Assim o VPS faz `docker compose pull` sem autenticar. (Alternativa: manter privada e
rodar `docker login ghcr.io` no VPS com um Personal Access Token de escopo `read:packages`.)

## Passo 1 — DNS na Hostinger

Painel Hostinger (**Domínios → DNS / Nameservers → Gerenciar registros DNS**), crie:

| Tipo | Nome (host)       | Valor (aponta para) | TTL  |
| ---- | ----------------- | ------------------- | ---- |
| A    | `speed-violation` | `157.180.124.235`   | 3600 |

Publica `speed-violation.nebulax.com.br`. Confirme a propagação:

```bash
dig +short speed-violation.nebulax.com.br    # deve retornar 157.180.124.235
```

> O certificado só é emitido quando o DNS já resolve para o VPS e as portas 80/443 estão acessíveis.

## Passo 2 — Liberar o firewall do VPS

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
```

## Passo 3 — Obter os arquivos no VPS

Basta o compose e o Caddyfile. A forma mais simples é clonar o repositório
(não haverá build a partir dele; usaremos a imagem do GHCR):

```bash
git clone https://github.com/oswaldoschermach/speed-violation-service.git
cd speed-violation-service
```

> Alternativa sem git: copie apenas `docker/prod/compose.yaml` e `docker/prod/Caddyfile` para o VPS (ex.: `scp`), preservando o caminho `docker/prod/`.

## Passo 4 — Configurar o `.env`

```bash
cp docker/prod/.env.example docker/prod/.env
nano docker/prod/.env
```

Defina no mínimo:

```env
POSTGRES_DB=speed_violation
POSTGRES_USER=speed_violation
POSTGRES_PASSWORD=<senha_forte_aqui>
APP_DOMAIN=speed-violation.nebulax.com.br
LETSENCRYPT_EMAIL=voce@nebulax.com.br
APP_IMAGE=ghcr.io/oswaldoschermach/speed-violation-service:latest
```

## Passo 5 — Subir a stack (pull da imagem)

No VPS use `docker-compose` (v1, com hífen) ou `docker compose` (plugin v2), conforme instalado:

```bash
docker-compose -f docker/prod/compose.yaml --env-file docker/prod/.env pull
docker-compose -f docker/prod/compose.yaml --env-file docker/prod/.env up -d
```

Equivalente com plugin v2:

```bash
docker compose -f docker/prod/compose.yaml --env-file docker/prod/.env pull
docker compose -f docker/prod/compose.yaml --env-file docker/prod/.env up -d
```

Acompanhe a emissão do certificado:

```bash
docker compose -f docker/prod/compose.yaml --env-file docker/prod/.env ps
docker compose -f docker/prod/compose.yaml --env-file docker/prod/.env logs -f caddy
```

> Sem CI/imagem publicada ainda? Dá para buildar do fonte no VPS: `... up -d --build` (requer o repositório inteiro e ~2 GB de RAM).

## Passo 6 — Verificar

```bash
curl -s https://speed-violation.nebulax.com.br/actuator/health      # {"status":"UP"}

curl -s -X POST https://speed-violation.nebulax.com.br/api/v1/violations/evaluate \
  -H "Content-Type: application/json" \
  -H "x-origin: FIXED" \
  -d '{"licensePlate":"ABC1D23","measuredSpeed":92,"speedLimit":60,"equipmentId":"RAD-CWB-001","captureTimestamp":"2026-06-08T14:30:00Z"}'
```

- Swagger UI: `https://speed-violation.nebulax.com.br/swagger-ui.html`
- OpenAPI JSON: `https://speed-violation.nebulax.com.br/api-docs`

Depois de validar, atualize a linha **API hospedada** na seção [Entrega](../README.md#entrega) do README.

## Atualizações (nova versão) — deploy manual

Cada push na `main` republica a imagem `:latest` no GHCR. No VPS:

```bash
cd <pasta_do_deploy>
git pull --ff-only        # atualiza compose.yaml/Caddyfile
docker compose -f docker/prod/compose.yaml --env-file docker/prod/.env pull
docker compose -f docker/prod/compose.yaml --env-file docker/prod/.env up -d
```

## Deploy automático via SSH (opcional)

Com isto, cada push na `main` faz o GitHub Actions **conectar por SSH no VPS** e rodar
`git pull + compose pull + up -d` sozinho. O Actions **não envia arquivos**: ele apenas
executa comandos numa pasta que já contém o repositório (`DEPLOY_PATH`).

### 1. Preparar a pasta de deploy no VPS

Escolha um caminho e deixe a stack pronta lá (Passos 3–5 já fazem isso). Exemplo:

```bash
# no VPS, como o usuário que fará o deploy (precisa estar no grupo docker)
cd ~
git clone https://github.com/oswaldoschermach/speed-violation-service.git
cd speed-violation-service
cp docker/prod/.env.example docker/prod/.env && nano docker/prod/.env   # preencher
```

O caminho completo (ex.: `/home/deploy/speed-violation-service`) é o valor de `DEPLOY_PATH`.

### 2. Gerar uma chave SSH dedicada ao Actions

Na sua máquina (ou no VPS):

```bash
ssh-keygen -t ed25519 -f gha_deploy_key -C "github-actions-deploy" -N ""
```

- **Chave pública** (`gha_deploy_key.pub`): adicione ao VPS, no usuário do deploy:
  ```bash
  cat gha_deploy_key.pub >> ~/.ssh/authorized_keys   # no VPS
  ```
- **Chave privada** (`gha_deploy_key`): vai como secret no GitHub (passo 3).

Garanta que o firewall do VPS permite SSH (`sudo ufw allow OpenSSH`).

### 3. Cadastrar secrets e variables no GitHub

Repositório → **Settings → Secrets and variables → Actions**:

**Secrets** (aba *Secrets*):

| Nome | Valor |
| ---- | ----- |
| `SSH_HOST` | `157.180.124.235` |
| `SSH_USER` | usuário do deploy no VPS (ex.: `deploy`) |
| `SSH_KEY`  | conteúdo **completo** da chave privada `gha_deploy_key` |

**Variables** (aba *Variables*):

| Nome | Valor |
| ---- | ----- |
| `DEPLOY_ENABLED` | `true` |
| `DEPLOY_PATH` | caminho no VPS (ex.: `/home/deploy/speed-violation-service`) |

> O job `deploy` só roda quando `DEPLOY_ENABLED=true`. Sem isso, o CI apenas testa e publica a imagem (deploy fica manual).

### 4. Pronto

No próximo push na `main`: **testes → build → push GHCR → SSH no VPS → `git pull` + `compose pull` + `up -d`**. Acompanhe em *Actions* e valide com o `curl` do Passo 6.

## Operação

```bash
docker compose -f docker/prod/compose.yaml --env-file docker/prod/.env logs -f app
docker compose -f docker/prod/compose.yaml --env-file docker/prod/.env down      # parar
docker compose -f docker/prod/compose.yaml --env-file docker/prod/.env down -v   # parar + apagar dados (irreversível)
```

## Problemas comuns

| Sintoma | Causa provável | Ação |
| ------- | -------------- | ---- |
| `docker compose pull` pede login | imagem GHCR ainda privada | Faça o Passo 0 (tornar pública) ou `docker login ghcr.io` |
| Certificado não emite / erro ACME no log do Caddy | DNS não propagou ou portas 80/443 bloqueadas | Confirme `dig` e o firewall; aguarde a propagação |
| `502`/`503` no domínio | app ainda subindo (`start_period` 90s) ou unhealthy | `logs -f app`; aguarde `healthy` |
| Swagger com URLs `http`/host errado | proxy sem forward headers | Já tratado por `forward-headers-strategy: framework` no perfil `prod` |
| App não sobe: falha de conexão com o banco | credenciais/`.env` incorretos | Revise `docker/prod/.env` e `logs -f postgres` |
