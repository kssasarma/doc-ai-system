# Documentation Bot

AI-powered Q&A service for documentation with chat context awareness and fast pgvector retrieval.

## Features

- Multi-turn conversations with context preservation
- Vector similarity search using pgvector
- Automatic chat summarization for long conversations
- Product/version filtering
- Fast retrieval (<2s target)
- Stateless REST API

## Prerequisites

- Java 21
- PostgreSQL 15+ with pgvector extension
- OpenAI-compatible API with:
  - `gpt-4o-mini` (chat model)
  - `snowflake-arctic` (embedding model)
  - Base URL: https://api.openai.com/

## Database Setup

Same database as document-ingestor:

```sql
CREATE DATABASE docai;
\c docai;
CREATE EXTENSION IF NOT EXISTS vector;
```

## Configuration

Edit `application.yml`:

```yaml
bot:
  max-context-messages: 10      # Messages to keep in context
  summary-threshold: 15          # Trigger summary after N messages
  top-k-results: 7              # Number of chunks to retrieve
  chat-expiry-hours: 24         # Chat session TTL
```

## Running

```bash
mvn spring-boot:run
```

## API Usage

### Ask a Question

```bash
curl -X POST http://localhost:8082/api/chat/query \
  -H "Content-Type: application/json" \
  -d '{
    "product": "case360",
    "version": "23.4",
    "question": "How do I configure SSL?"
  }'
```

Response:
```json
{
  "chatId": "uuid-here",
  "answer": "To configure SSL...",
  "sources": [
    {
      "document": "installation.pdf",
      "chunkId": "chunk-uuid"
    }
  ]
}
```

### Continue Conversation

Include the `chatId` from previous response:

```bash
curl -X POST http://localhost:8082/api/chat/query \
  -H "Content-Type: application/json" \
  -d '{
    "chatId": "uuid-from-previous-response",
    "product": "case360",
    "version": "23.4",
    "question": "What about the certificate path?"
  }'
```

## Architecture

- **ChatService**: Orchestrates query processing
- **VectorSearchService**: pgvector similarity search
- **ContextManager**: Chat history management
- **AnswerGenerationService**: LLM-based answer generation
- **ChatSummaryService**: Async conversation summarization

## Performance

- Vector index usage via pgvector
- Context token limiting
- Async summarization
- Stateless API design
