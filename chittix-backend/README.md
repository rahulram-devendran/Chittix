# Company Brain (backend MVP)

Spring Boot 3.4 / Java 21 / Maven backend that answers questions from the existing FalkorDB graph `company_brain`.
Read-only: it never creates, resets or deletes the database or graph. No LLM in this milestone.

FalkorDB client: official `com.falkordb:jfalkordb:0.12.0` (https://github.com/FalkorDB/JFalkorDB).

## Prerequisites (Windows 11, PowerShell)
- JDK 21 on PATH: `java -version`
- FalkorDB container running (existing): `docker ps --filter name=company-brain-db`
- Maven is NOT required; the wrapper (`mvnw.cmd`) downloads it on first run.

## Build and test (no FalkorDB needed for tests)
```powershell
cd company-brain
.\mvnw.cmd clean test
.\mvnw.cmd clean package
```

## Run
```powershell
.\mvnw.cmd spring-boot:run
# or
java -jar .\target\company-brain-0.1.0.jar
```
Defaults: `localhost:6379`, graph `company_brain`, port `8080`.
Override with env vars (PowerShell):
```powershell
$env:FALKORDB_HOST = "localhost"; $env:FALKORDB_PORT = "6379"; $env:FALKORDB_GRAPH = "company_brain"
$env:SERVER_PORT = "8080"
# optional auth: $env:FALKORDB_USERNAME, $env:FALKORDB_PASSWORD
```

## Test the APIs (second PowerShell window)
```powershell
# A. Health (200 if FalkorDB reachable, 503 with same JSON body if not)
Invoke-RestMethod http://localhost:8080/api/health

# B. Payments context (real multi-hop Cypher)
Invoke-RestMethod http://localhost:8080/api/demo/payments-context | ConvertTo-Json -Depth 6

# C. Ask Company Brain
$body = @{ question = "Why did we choose PostgreSQL for the Payment Service?" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/brain/ask -ContentType "application/json" -Body $body | ConvertTo-Json -Depth 6

# C (no evidence -> answered=false, "Insufficient evidence...")
$body = @{ question = "What is our vacation policy?" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/brain/ask -ContentType "application/json" -Body $body | ConvertTo-Json -Depth 6

# C (validation error -> 400)
try { Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/brain/ask -ContentType "application/json" -Body '{"question":""}' }
catch { $_.ErrorDetails.Message }
```

## How retrieval works
- `/api/demo/payments-context`: matches `Decision-[:AFFECTS]->Project-[:USES]->System` plus `Decision-[:SUPPORTED_BY]->Document`, for projects whose name contains `brain.demo.project-keyword` (default `payment`). Relationship names in the evidence path come from `type(r)` in the query result.
- `/api/brain/ask`: same chain, filtered in Cypher to rows whose project, system or decision title is mentioned in the question (passed as a `$question` parameter). No rows -> `answered:false`.
- Answers are labeled `PRELIMINARY` and built only from retrieved facts.

## Layout
`config` (driver bean) · `controller` · `service` (`GraphClient` wraps JFalkorDB) · `dto` · `exception`
