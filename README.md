# Doc AI System

Enterprise documentation ingestion and Q&A system using PostgreSQL with pgvector.

## Architecture

Two Spring Boot microservices:

1. **document-ingestor** (port 8081) - Monitors directory and ingests docs
2. **documentation-bot** (port 8082) - Answers questions with chat context

## Quick Start

### 1. Prerequisites

```bash
# Install PostgreSQL 15+
# Set OPENAI_API_KEY environment variable (if required)
export OPENAI_API_KEY=your-api-key
```

### 2. Database Setup

```bash
psql -U postgres -f setup-database.sql
```

### 3. Start Services

```bash
# Terminal 1: Document Ingestor
cd document-ingestor
mvn spring-boot:run

# Terminal 2: Documentation Bot
cd documentation-bot
mvn spring-boot:run
```

### 4. Add Documents

Place files in `document-ingestor/watched-docs/`:

```
case360-23.4-installation.pdf
content-server-22.2-admin.chm
```

Naming: `product-version-documentName.ext`

### 5. Ask Questions

```bash
curl -X POST http://localhost:8082/api/chat/query \
  -H "Content-Type: application/json" \
  -d '{
    "product": "case360",
    "version": "23.4",
    "question": "How do I install the server?"
  }'
```

## Features

### Document Ingestor
- ✅ Auto-detects file changes (PDF, CHM)
- ✅ Hash-based deduplication
- ✅ Semantic chunking (800 tokens, 100 overlap)
- ✅ Vector embeddings via Ollama
- ✅ pgvector storage

### Documentation Bot
- ✅ Multi-turn conversations
- ✅ Context-aware retrieval
- ✅ Auto-summarization (>15 messages)
- ✅ Product/version filtering
- ✅ Source citations

## Tech Stack

- **Framework**: Spring Boot 3.4.2
- **Java**: 21
- **Database**: PostgreSQL + pgvector
- **AI**: Spring AI + OpenAI-compatible API
- **Document Parsing**: Apache Tika

## API Endpoints

### Document Ingestor
- `GET /api/ingest/status` - Ingestion statistics
- `GET /api/ingest/documents` - List all documents
- `POST /api/ingest/reload` - Trigger directory scan

### Documentation Bot
- `POST /api/chat/query` - Ask questions (with optional chatId)

## Configuration

Both services use `application.yml`. Key settings:

```yaml
# document-ingestor
ingestor:
  watch-directory: ./watched-docs
  chunk-size: 800
  chunk-overlap: 100

# documentation-bot
bot:
  max-context-messages: 10
  top-k-results: 7
  summary-threshold: 15
```

## Project Structure

```
doc-ai-system/
├── document-ingestor/
│   ├── src/main/java/com/docai/ingestor/
│   │   ├── domain/          # Entities, repositories
│   │   ├── application/     # Services, parsers
│   │   ├── infrastructure/  # File watcher
│   │   └── adapter/         # REST controllers
│   └── pom.xml
├── documentation-bot/
│   ├── src/main/java/com/docai/bot/
│   │   ├── domain/          # Entities, repositories
│   │   ├── application/     # Chat, search, context services
│   │   └── adapter/         # REST controllers
│   └── pom.xml
└── setup-database.sql
```

## Performance Tuning

### Vector Index Creation

After ingesting documents, create pgvector index:

```sql
CREATE INDEX ON document_chunks 
USING ivfflat (embedding vector_cosine_ops) 
WITH (lists = 100);
```

### Monitoring

```bash
# Check ingestion status
curl http://localhost:8081/api/ingest/status

# View documents
curl http://localhost:8081/api/ingest/documents
```

## License

MIT
