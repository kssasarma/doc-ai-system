# Monitoring

Both services expose Spring Boot Actuator endpoints for health checks, Prometheus metrics, and structured JSON logs.

---

## Health Endpoints

| Endpoint | Purpose | Notes |
|---|---|---|
| `GET /actuator/health` | Combined liveness + readiness | Returns `{"status":"UP"}` when healthy |
| `GET /actuator/health/liveness` | Kubernetes liveness probe | Fails → pod restarted |
| `GET /actuator/health/readiness` | Kubernetes readiness probe | Fails → pod removed from load balancer |
| `GET /actuator/info` | Build and version info | |

Available on both services (ports 8081 and 8082).

### Example health check

```bash
curl http://localhost:8082/actuator/health
# {"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"}}}

curl http://localhost:8081/actuator/health
# {"status":"UP","components":{"db":{"status":"UP"},"s3":{"status":"UP"}}}
```

The `readiness` probe checks actual dependency health (database connectivity, S3 reachability) before declaring the pod ready for traffic.

---

## Prometheus Metrics

```
GET /actuator/prometheus
```

Scraped by Prometheus at this endpoint on both services. Key metrics:

### HTTP

| Metric | Description |
|---|---|
| `http_server_requests_seconds` | Latency histogram by `uri`, `method`, `status` |
| `http_server_requests_seconds_count` | Request count (derive rate from this) |

### JVM

| Metric | Description |
|---|---|
| `jvm_memory_used_bytes` | Heap and non-heap usage by area |
| `jvm_gc_pause_seconds` | GC pause latency |
| `jvm_threads_live_threads` | Live thread count |

### HikariCP (database connection pool)

| Metric | Description |
|---|---|
| `hikaricp_connections_active` | Currently active connections |
| `hikaricp_connections_pending` | Threads waiting for a connection |
| `hikaricp_connections_timeout_total` | Cumulative connection timeouts (alert on non-zero) |

### Resilience4j (LLM circuit breaker)

| Metric | Description |
|---|---|
| `resilience4j_circuitbreaker_state` | `0 = CLOSED` (healthy), `1 = OPEN` (LLM unavailable), `2 = HALF_OPEN` |
| `resilience4j_circuitbreaker_calls_total` | LLM call outcomes by `kind` (successful / failed / not_permitted) |
| `resilience4j_circuitbreaker_buffered_calls` | Calls in the sliding window |

Alert on `resilience4j_circuitbreaker_state == 1` — it means all LLM calls are failing.

### Embedding cache (Redis)

| Metric | Description |
|---|---|
| `cache.gets{result="hit",name="embeddings"}` | Cache hit count |
| `cache.gets{result="miss",name="embeddings"}` | Cache miss count (triggers OpenAI call) |

Track `hit / (hit + miss)` as the cache hit ratio. A low ratio after warm-up indicates the cache TTL or Redis memory limit is too small.

### Bucket4j (rate limiting)

| Metric | Description |
|---|---|
| `bucket4j_available_tokens` | Remaining tokens per user/IP bucket |

---

## Prometheus Configuration

Add the following to your `prometheus.yml` scrape config:

```yaml
scrape_configs:
  - job_name: 'docai-bot'
    static_configs:
      - targets: ['documentation-bot:8082']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s

  - job_name: 'docai-ingestor'
    static_configs:
      - targets: ['document-ingestor:8081']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s
```

In Kubernetes, use the Prometheus Operator `ServiceMonitor` CRD instead.

---

## Grafana Dashboards

Recommended imports from the Grafana dashboard library:

| Dashboard | ID | Description |
|---|---|---|
| JVM Micrometer | 4701 | Heap, GC, threads, and classloader |
| Spring Boot 3.x Statistics | 11378 | HTTP request rates, latencies, error rates |
| HikariCP | 7103 | Connection pool health |
| Redis Dashboard | 763 | Redis memory, evictions, hit rate |

After importing, set the `datasource` variable to your Prometheus instance and filter by `job="docai-bot"` or `job="docai-ingestor"`.

### Recommended alerts

| Alert | Condition | Severity |
|---|---|---|
| LLM circuit open | `resilience4j_circuitbreaker_state{name="llm"} == 1` | Critical |
| DB connection exhaustion | `hikaricp_connections_pending > 5 for 2m` | Warning |
| High error rate | `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05` | Critical |
| Slow responses | `histogram_quantile(0.95, http_server_requests_seconds_bucket) > 5` | Warning |
| Embedding cache cold | `rate(cache.gets{result="miss"}[5m]) / rate(cache.gets[5m]) > 0.8 for 10m` | Info |

---

## Structured Logging

Both services emit structured JSON logs in production. Every log line includes:

| Field | Value |
|---|---|
| `timestamp` | ISO-8601 UTC |
| `level` | `INFO`, `WARN`, `ERROR`, `DEBUG` |
| `service` | `documentation-bot` or `document-ingestor` |
| `tenantId` | UUID of the current tenant (set from `TenantContext`) |
| `userId` | UUID of the authenticated user |
| `traceId` | Micrometer trace ID (propagated across async boundaries) |
| `message` | Log message |

Additional fields per context:
- Ingestion jobs: `documentId`
- LLM calls: `provider`, `model`, `promptTokens`, `completionTokens`
- Requests: `method`, `uri`, `status`, `durationMs`

### Log aggregation

Ship JSON logs to your preferred aggregation stack:

- **ELK:** Filebeat → Logstash → Elasticsearch → Kibana
- **Loki:** Promtail → Loki → Grafana (filter by `service` label)
- **CloudWatch / Stackdriver / Azure Monitor:** Docker log driver forwarding

### Querying logs

```
# All errors for a tenant in the last hour
{service="documentation-bot", level="ERROR", tenantId="<uuid>"}

# Slow LLM calls
{service="documentation-bot"} | json | durationMs > 5000 and message =~ "LLM"

# Ingestion failures
{service="document-ingestor", level="ERROR"} | json | message =~ "ingestion"
```

Include `traceId` in support tickets — it correlates all log lines from a single request across both services.

---

## Log Level Configuration

Set log levels at runtime without redeployment via Spring Boot Actuator:

```bash
# Set a package to DEBUG
curl -X POST http://localhost:8082/actuator/loggers/com.docai.bot.application \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'

# Reset to default
curl -X POST http://localhost:8082/actuator/loggers/com.docai.bot.application \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":null}'
```

This is especially useful for debugging LLM routing or retrieval issues without restarting pods.
