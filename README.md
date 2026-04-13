# RepoIndex

**LLM-powered code intelligence service** that dynamically generates and executes [OpenRewrite](https://docs.openrewrite.org/) recipes to analyze local repositories using natural language queries.

## Overview

RepoIndex parses your local repositories into Lossless Semantic Trees (LSTs) using OpenRewrite, then uses an LLM (via Ollama or any Spring AI-supported provider) to dynamically generate OpenRewrite visitors based on your natural language questions. The generated recipes are compiled and executed at runtime against the parsed ASTs, producing precise, structurally-aware answers.

```
User Query → LLM generates OpenRewrite Recipe → Compile → Execute against LSTs → Format Answer
```

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   Chat UI (HTMX)                    │
├─────────────────────────────────────────────────────┤
│               QueryOrchestrator                     │
│  ┌──────────┐  ┌──────────┐  ┌─────────────────┐  │
│  │  Recipe   │→│ Compiler │→│   Execution      │  │
│  │ Generator │  │ (Kotlin  │  │   (OpenRewrite)  │  │
│  │  (LLM)   │  │  Script) │  │                  │  │
│  └──────────┘  └──────────┘  └─────────────────┘  │
├─────────────────────────────────────────────────────┤
│           Repository Index Service                  │
│     Java Parser │ Kotlin Parser │ Scala (planned)   │
├─────────────────────────────────────────────────────┤
│              In-Memory LST Store                    │
└─────────────────────────────────────────────────────┘
```

## Tech Stack

| Component | Choice |
|---|---|
| Language | Kotlin |
| Framework | Spring Boot 3.4.x |
| Build | Gradle (Kotlin DSL) |
| JDK | 21 |
| LLM Integration | Spring AI + Ollama (swappable to OpenAI/Anthropic/Azure) |
| Code Parsing | OpenRewrite (rewrite-core, rewrite-java, rewrite-kotlin) |
| UI | Thymeleaf + HTMX |
| Container | Docker + Docker Compose |

## Prerequisites

- **JDK 21** or later
- **Docker** installed and running (for automatic Ollama startup)
- A code model pulled in Ollama (e.g., `codellama:13b`)

## Quick Start

### 1. Run the application

```bash
# Clone this repo
git clone https://github.com/edg7007-netizen/repo-index.git
cd repo-index

# Run with Gradle — Ollama starts automatically via Docker Compose
./gradlew bootRun
```

Spring Boot's Docker Compose integration will automatically start Ollama when you run the application and stop it when you shut down. No manual Ollama setup needed!

> **Note:** The first run will pull the Ollama Docker image, which may take a few minutes. You'll also need to pull a model into Ollama once it's running:
> ```bash
> docker exec -it $(docker ps -qf "ancestor=ollama/ollama") ollama pull codellama:13b
> ```

### 2. Open the chat UI

Navigate to [http://localhost:8080](http://localhost:8080)

1. Enter the path to a local repository and click **Index**
2. Select the repository from the sidebar
3. Ask questions about the code!

### Docker Compose (production deployment)

```bash
# Set the path to your repositories
export REPOS_PATH=/path/to/your/repos

# Start the full stack (app + Ollama)
docker compose -f docker-compose.yml up --build
```

## Usage Examples

Once a repository is indexed, you can ask questions like:

- *"Find all classes that implement the Repository interface"*
- *"List all methods annotated with @Transactional"*
- *"Which classes have more than 10 methods?"*
- *"Find all usages of deprecated methods"*
- *"Show me classes that don't have any tests"*

## REST API

### Repository Management

```bash
# Index a repository
curl -X POST http://localhost:8080/api/repos \
  -H "Content-Type: application/json" \
  -d '{"path": "/path/to/your/repo"}'

# List indexed repositories
curl http://localhost:8080/api/repos

# Remove a repository
curl -X DELETE http://localhost:8080/api/repos/{id}
```

### Query

```bash
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"repositoryId": "...", "query": "Find all singleton classes"}'
```

## Supported Languages

- ✅ Java (full LST parsing)
- ✅ Kotlin (full LST parsing)
- 🔄 Scala (file detection, full parsing planned)

## Configuration

Key configuration in `application.yml`:

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434   # Ollama endpoint
      chat:
        model: codellama:13b             # LLM model to use
        options:
          temperature: 0.2               # Lower = more deterministic

repoindex:
  max-retries: 3                         # Max LLM retry attempts for compilation errors
  index:
    max-file-size-kb: 500                # Skip files larger than this
    supported-extensions:                # File types to parse
      - .java
      - .kt
      - .kts
      - .scala
```

### Switching LLM Providers

Thanks to Spring AI's abstraction layer, switching from Ollama to a cloud provider requires only a dependency and configuration change:

```kotlin
// build.gradle.kts — swap the starter
implementation("org.springframework.ai:spring-ai-starter-model-openai")
```

```yaml
# application.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        model: gpt-4
```

## Development

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run the app
./gradlew bootRun
```

## License

MIT