# AI Models & Best Practices

Docs-inator does not pin a platform-wide LLM. Every tenant admin picks a **provider** (`openai` or `anthropic`), a **chat model**, an **embedding model**, and optionally separate **simple/complex/re-rank models** in Settings → AI Configuration (see [Multi-Tenancy — Tenant LLM Configuration](multi-tenancy.md#tenant-llm-configuration)). Model IDs are free-text — there is no code change or deployment needed to move a tenant onto a newer model, only an admin updating a string and saving.

This page is a living reference for **which model to put in that string**, and how the platform's own routing features (smart routing, dedicated re-rank model) are meant to be used. Model lineups move fast; treat the tables below as a snapshot and re-check the linked provider pages before committing to a choice for a new tenant.

> Last checked: September 2026 — [Anthropic model overview](https://platform.claude.com/docs/en/about-claude/models/overview) · [OpenAI model docs](https://developers.openai.com/api/docs/models)

---

## Where each model ID is used

| `TenantLLMConfig` field | Used by | Notes |
|---|---|---|
| `chatModel` | `AnswerGenerationService` via `LLMRouter` | Used when `routingEnabled=false` — one model for every chat turn |
| `simpleModel` / `complexModel` | Same, when `routingEnabled=true` | `QueryAnalyzerService` classifies the incoming query; simple factual lookups go to `simpleModel`, multi-hop/ambiguous/synthesis queries go to `complexModel` |
| `rerankModel` | `ReRankingService` | Cheap relevance judgment over retrieved chunks, not answer generation — falls back to `simpleModel`/`chatModel` when unset |
| `embeddingModel` | `document-ingestor` at ingest time, `VectorSearchService` at query time | Must stay **consistent for a tenant's whole corpus** — changing it requires re-embedding all existing documents, since vectors from different models aren't comparable |

Smart routing (`routingEnabled=true`) is the single biggest lever for cost without a quality hit: point `complexModel` at your best model and `simpleModel` at your cheapest, and let `QueryAnalyzerService` do the sorting. Most tenant traffic in a documentation-Q&A workload is simple lookups, so this typically cuts spend well below running everything on the complex model.

---

## Anthropic

| Model | API ID | Best for | Input / Output ($ per MTok) | Context |
|---|---|---|---|---|
| Claude Opus 5 | `claude-opus-5` | Complex agentic reasoning, hardest support questions, cross-document synthesis | $5 / $25 | 1M |
| Claude Sonnet 5 | `claude-sonnet-5` | Best speed/intelligence balance — good default `chatModel` or `complexModel` for most tenants | $2 / $10 | 1M |
| Claude Haiku 4.5 | `claude-haiku-4-5-20251001` | Fastest, near-frontier — `simpleModel` and `rerankModel` | $1 / $5 | 200K |
| Claude Fable 5.1 | `claude-fable-5-1` | Demanding long-horizon reasoning where Opus 5 falls short on your own evals | $10 / $50 | 1M |

Anthropic does not offer an embeddings endpoint — `supportsEmbeddings()` returns `false` for the `anthropic` provider bean, so a tenant on Anthropic chat still needs an OpenAI (or OpenAI-compatible) `embeddingProvider`/`embeddingApiKey`. This is already handled by the dual-key design in `LlmConfigForm`.

Pinned dated IDs (e.g. `claude-haiku-4-5-20251001`) never change behavior under you; dateless IDs from the 4.6 generation onward (`claude-sonnet-5`, `claude-opus-5`, `claude-fable-5-1`) are themselves pinned snapshots, not floating aliases — check [Model IDs and versioning](https://platform.claude.com/docs/en/about-claude/models/model-ids-and-versions) before assuming otherwise for older models.

---

## OpenAI

| Model | API ID | Best for | Context |
|---|---|---|---|
| GPT-5.6 Sol | `gpt-5.6-sol` (alias: `gpt-5.6`) | Flagship tier — complex reasoning and coding, good `complexModel` choice | 1.05M |
| GPT-5.6 Terra | `gpt-5.6-terra` | Balanced intelligence/cost — reasonable default `chatModel` | 1.05M |
| GPT-5.6 Luna | `gpt-5.6-luna` | Cheapest, high-volume — `simpleModel` / `rerankModel` | 1.05M |
| text-embedding-3-small | `text-embedding-3-small` | Default embedding model — 1536-dim, current platform default | — |
| text-embedding-3-large | `text-embedding-3-large` | Higher-fidelity embeddings (up to 3072-dim) when retrieval quality matters more than storage/cost | — |

OpenAI has not shipped a successor to the `text-embedding-3` family — `text-embedding-3-small` (the platform default in `TenantLLMConfig`) is still current, not legacy. There's no urgency to move tenants off it; `text-embedding-3-large` is worth evaluating only if a tenant's retrieval quality (measured via the answer confidence scores / documentation gap reports) is actually falling short.

---

## Suggested pairings for a new tenant

| Priority | Chat / complex | Simple / re-rank | Embedding |
|---|---|---|---|
| Best quality | `claude-opus-5` or `gpt-5.6-sol` | `claude-haiku-4-5-20251001` or `gpt-5.6-luna` | `text-embedding-3-small` |
| Balanced (recommended default) | `claude-sonnet-5` or `gpt-5.6-terra` | `claude-haiku-4-5-20251001` or `gpt-5.6-luna` | `text-embedding-3-small` |
| Cost-sensitive | `claude-haiku-4-5-20251001` or `gpt-5.6-terra` | same as chat | `text-embedding-3-small` |

Mixed-provider setups work too — e.g. Anthropic for chat quality with OpenAI for embeddings (the only provider that serves them). Whatever you pick, always click **Test connection** in the AI Configuration form before saving; it validates the key and model ID against the live provider before it's stored.

---

## RAG-specific practices this platform already enforces

These aren't configuration choices — they're built into the pipeline and worth knowing when reasoning about model quality:

- **Grounding threshold.** `BOT_MIN_SIMILARITY_THRESHOLD` (default `0.55`) excludes low-relevance chunks from context entirely. A query with nothing above threshold returns an honest "not found" rather than letting the model improvise from weak context — this matters more for answer quality than the choice of chat model.
- **Hybrid retrieval + re-ranking.** Dense (cosine) and lexical (`tsvector`) search results are fused with RRF and re-ranked (MMR + optional LLM re-rank pass) before ever reaching the chat model, so the chat model only sees the most relevant, de-duplicated chunks — keep `rerankModel` cheap and fast rather than reusing your most expensive model for it.
- **No platform fallback key.** Every call authenticates with the tenant's own key (AES-256-GCM at rest); there is no shared platform key that could leak usage or cost across tenants. See [Security](security.md) for the full key-handling model.
- **Model ID is decoupled from ingestion.** Changing `embeddingModel` does not retroactively re-embed existing documents — `LLMRouter.embed(text, model)` lets search re-use the model recorded on each document at ingest time, so old and new embeddings can coexist during a migration rather than breaking search outright.

---

## Keeping this page current

Because model IDs and pricing change faster than this repository does, prefer the provider's own docs as the source of truth and treat this page as a starting point:

- Anthropic: [platform.claude.com/docs/en/about-claude/models/overview](https://platform.claude.com/docs/en/about-claude/models/overview) · [choosing a model](https://platform.claude.com/docs/en/about-claude/models/choosing-a-model)
- OpenAI: [developers.openai.com/api/docs/models](https://developers.openai.com/api/docs/models)
