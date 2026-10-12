# Scripts

Helpers for the local stack. Each script has a Bash version (`.sh`, for Linux,
macOS, Git Bash, WSL and CI) and a PowerShell version (`.ps1`, Windows
PowerShell 5.1+ and PowerShell 7+). Both versions behave the same.

| Script | What it does |
|---|---|
| `env-up` | `docker compose up -d`, then waits until PostgreSQL accepts connections and, once Keycloak is in `docker-compose.yml` (milestone 3), until the realm is served |
| `env-down` | `docker compose down`; with `--volumes` / `-Volumes` also deletes the data (resets the seed) |
| `get-token` | Prints an access token for a fixture user (`admin`, `analyst`, `shop-a`) |
| `smoke-test` | Calls the running API with each role and checks the status codes from the specs. Exit code 1 if any check fails |
| `rate-limit-check` | Sends requests as one user until it gets `429`, then checks `Retry-After` and the Problem Details body |

## Usage

```bash
scripts/env-up.sh
./mvnw spring-boot:run           # in another terminal
scripts/get-token.sh shop-a
scripts/smoke-test.sh
scripts/rate-limit-check.sh shop-a
scripts/env-down.sh --volumes
```

```powershell
.\scripts\env-up.ps1
.\mvnw.cmd spring-boot:run       # in another terminal
.\scripts\get-token.ps1 -User shop-a
.\scripts\smoke-test.ps1
.\scripts\rate-limit-check.ps1 -User shop-a
.\scripts\env-down.ps1 -Volumes
```

If PowerShell blocks the scripts (execution policy), run them with
`powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1`.

## Configuration

Environment variables (same names in both shells):

| Variable | Default |
|---|---|
| `API_URL` | `http://localhost:8080` |
| `KEYCLOAK_URL` | `http://localhost:8180` |
| `REALM` | `partnerguard` |
| `CLIENT_ID` | `partnerguard-cli` |
| `POSTGRES_USER` | `partnerguard` |
| `TIMEOUT_SECONDS` (env-up) | `180` |
| `MAX_CALLS` (rate-limit-check) | `100` |

## Notes

- **Assumptions about `docker-compose.yml`:** a service named `postgres`; from
  milestone 3, a service named `keycloak` published on port 8180 with the realm
  imported from `keycloak/realm-export.json`.
- PostgreSQL is published on `127.0.0.1:${POSTGRES_PORT:-5432}`. If a local
  PostgreSQL already uses 5432, set `POSTGRES_PORT=5433` in `.env` (copied from
  `.env.example`). Both Compose and the app's `local` profile read it.
- All users, ids and expected values come from
  [specs/fixtures.md](../specs/fixtures.md). Each check prints the spec
  criterion ID it covers.
- `smoke-test` only sends requests that must fail before any write
  (`400/403/404/409`), so it leaves the seed data unchanged **if the API is
  correct**. If a check fails, a request may have changed data; reset with
  `env-down --volumes` and `env-up`.
- `rate-limit-check` empties the user's bucket. Wait about a minute before
  running `smoke-test` again with the same user.
- Requirements: `curl` 7.76+ (ships with Windows 10+ as `curl.exe`) and Docker
  Compose v2. No `jq` needed.
- After `git init`, mark the Bash scripts executable:
  `git update-index --chmod=+x scripts/*.sh`.
