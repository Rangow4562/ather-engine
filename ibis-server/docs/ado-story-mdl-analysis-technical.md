# Azure DevOps Story (Technical): SQL Analysis API (aka "SQL HOC AD Query")

## Overview
- Provide a reliable SQL analysis service that inspects incoming SQL against the MDL (Model Definition Language) and returns a structured breakdown: relations, column sources, filters, groupings, and sortings.
- The Ibis Server proxies requests to the Java Engine and returns normalized JSON to clients.

## Architecture Summary
- Client → Ibis Server (`/v2/analysis`) → Java Engine (`/v2/analysis/sql`, `/v2/analysis/sqls`).
- Implementation lives in `app/mdl/analyzer.py` and is exposed via `app/routers/v2/analysis.py`.
- Data transfer objects: `AnalyzeSQLDTO`, `AnalyzeSQLBatchDTO` in `app/model/__init__.py`.

## Current State (Completed)
- Synchronous proxy using `httpx`:
  - `GET /v2/analysis/sql` → forwards to Engine `/v2/analysis/sql`.
  - `GET /v2/analysis/sqls` → forwards to Engine `/v2/analysis/sqls`.
- Payload includes base64-encoded `manifestStr` and raw `sql` or `sqls`.
- Errors are mapped to `atherError` with appropriate user-facing messages.
- Tests cover core scenarios: select-all, group-by, join, where, order-by, and multi-statement batches.
- Config uses `get_config().ather_engine_endpoint` with `httpx==0.28.1`.

## Work To Do (Technical)
- Migrate to async HTTP: use `httpx.AsyncClient` with timeouts and retries.
- Standardize requests: prefer `POST` with `json` body over `GET` with `content`.
- Input validation: verify `manifestStr` base64, non-empty `sql`/`sqls`; add Pydantic validators.
- Error handling: consistent `atherError` mapping, include correlation IDs in logs.
- Observability: add request/response metrics (latency, status), structured logs.
- Tests: add failure-path tests (connect errors, timeouts, 4xx/5xx), large batch tests.
- Docs: update API usage and deprecation guidance for v2.
- Performance: benchmark with large manifests and batches.

## API Contract
### Single SQL Analysis
- Method: `POST`
- Path: `/v2/analysis/sql`
- Request (JSON):
```json
{
  "manifestStr": "<base64 of MDL JSON>",
  "sql": "SELECT * FROM customer"
}
```
- Response (200 JSON):
```json
[
  {
    "relation": {"type": "TABLE", "tableName": "customer", "nodeLocation": {"line": 1, "column": 15}},
    "selectItems": [
      {
        "nodeLocation": {"line": 1, "column": 8},
        "exprSources": [{"expression": "custkey", "sourceDataset": "customer", "sourceColumn": "custkey"}]
      }
    ],
    "groupByKeys": [],
    "sortings": []
  }
]
```
- Error (example):
```json
{
  "code": "GENERIC_USER_ERROR",
  "message": "Invalid request payload",
  "phase": "MDL_EXTRACTION"
}
```

### Batch SQL Analysis
- Method: `POST`
- Path: `/v2/analysis/sqls`
- Request (JSON):
```json
{
  "manifestStr": "<base64 of MDL JSON>",
  "sqls": [
    "SELECT * FROM customer",
    "SELECT custkey, count(*) FROM customer GROUP BY 1"
  ]
}
```
- Response (200 JSON): array per input SQL, each an array of analysis blocks.

## Validation Rules
- `manifestStr`: must be valid base64 of JSON matching MDL schema; decode failures return `GENERIC_USER_ERROR`.
- `sql`: non-empty string.
- `sqls`: non-empty array of non-empty strings; reasonable upper bound (configurable), with clear error if exceeded.

## Configuration
- `ather_ENGINE_ENDPOINT` must be set; otherwise analysis endpoints are unavailable.
- Optional timeouts and retry/backoff settings exposed via config (e.g., `analysis.timeoutMs`, `analysis.maxRetries`).

## Error Handling
- Map engine errors to user-facing `atherError`:
  - Connect/timeout → `LEGACY_ENGINE_ERROR` with guidance.
  - 4xx from engine → `GENERIC_USER_ERROR` with response text.
  - 5xx from engine → `LEGACY_ENGINE_ERROR` and correlation ID.
- Include `x-request-id` correlation header when present.

## Observability
- Log fields: `requestId`, `endpoint`, `status`, `latencyMs`, `sqlLength`, `batchSize`.
- Emit counters/histograms: success/failure counts, latency per endpoint.

## Testing Strategy
- Unit tests: DTO validation, analyzer call semantics, error mapping.
- Integration tests: use `ASGITransport` and `AsyncClient` to simulate requests.
- Failure-path tests: connect error, timeout, 4xx/5xx.
- Performance: batch sizes, large manifests.

## Risks & Mitigations
- Changing method to `POST` may impact clients → support `GET` during transition and provide migration notes.
- Async migration could surface event loop issues → test in CI and staging.
- Retry logic may amplify load → use capped retries with jitter.

## Acceptance Criteria
- Async client with timeouts/retries; endpoints accept `POST` `json`.
- Input validation and consistent error mapping in place.
- Logs include correlation IDs and metrics collected.
- Existing tests pass; new failure-path tests added.
- Documentation updated with examples and deprecation guidance.

## Definition of Done
- Implementation updated and validated in CI.
- Behavioral consistency verified against existing tests.
- Operational visibility improved (logs/metrics).
- Clear migration plan communicated.

