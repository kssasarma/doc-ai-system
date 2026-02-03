# Document Ingestor Service

Spring Boot service for ingesting documentation files into PostgreSQL with pgvector embeddings.

## Features

- Automatic directory monitoring for PDF and CHM files
- File naming convention: `product-version-documentName.ext`
- Hash-based change detection (idempotent)
- Semantic text chunking with configurable size and overlap
- Vector embeddings using Ollama
- PostgreSQL + pgvector storage

## Prerequisites

- Java 21
- PostgreSQL 15+ with pgvector extension
- OpenAI-compatible API at https://api.openai.com/

## Database Setup

```sql
CREATE DATABASE docai;
\c docai;
CREATE EXTENSION IF NOT EXISTS vector;
```

## Configuration

Edit `application.yml`:

```yaml
ingestor:
  watch-directory: ./watched-docs  # Directory to monitor
  chunk-size: 800                  # Tokens per chunk
  chunk-overlap: 100               # Overlap between chunks
```

## Running

```bash
mvn spring-boot:run
```

## API Endpoints

- `GET /api/ingest/status` - Get ingestion statistics
- `GET /api/ingest/documents` - List all documents
- `POST /api/ingest/reload` - Trigger directory re-scan

## File Naming Convention

Files must follow this pattern:
```
product-version-documentName.ext
```

Examples:
- `case360-23.4-installation.pdf`
- `content-server-22.2-admin.chm`
