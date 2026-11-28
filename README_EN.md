<p align="center">
  <img src="docs/logo.png" alt="ShangShu AI Logo" width="120" />
</p>

<h1 align="center">ShangShu AI</h1>

<p align="center">
  <strong>A self-hosted AI application platform built entirely on Java</strong><br />
  Multi-model gateway · Knowledge-base RAG · Agent workflows · Customer service · Security governance
</p>

<p align="center">
  <a href="https://github.com/jiangjiangbozan/ShangShu-AI/actions/workflows/ci.yml"><img src="https://github.com/jiangjiangbozan/ShangShu-AI/actions/workflows/ci.yml/badge.svg" alt="CI" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License" /></a>
  <img src="https://img.shields.io/badge/Java-21-orange.svg" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen.svg" alt="Spring Boot 4.1" />
  <img src="https://img.shields.io/badge/Spring%20AI-2.0-6DB33F.svg" alt="Spring AI" />
  <a href="https://github.com/jiangjiangbozan/ShangShu-AI/pulls"><img src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg" alt="PRs Welcome" /></a>
  <a href="https://github.com/jiangjiangbozan/ShangShu-AI/discussions"><img src="https://img.shields.io/badge/Discussions-join-blueviolet.svg" alt="Discussions" /></a>
</p>

<p align="center">
  <a href="README.md">中文</a> ·
  <a href="#-quick-start">Quick start</a> ·
  <a href="#-benchmarks">Benchmarks</a> ·
  <a href="#-documentation">Docs</a> ·
  <a href="#-roadmap">Roadmap</a> ·
  <a href="#-contributing--community">Contributing</a>
</p>

<p align="center">
  <img src="docs/screenshots/dashboard.png" alt="Dashboard" width="880" />
</p>

---

## Table of contents

- [Why ShangShu AI](#why-shangshu-ai)
- [Features](#-features)
- [Screenshots](#-screenshots)
- [Tech stack & architecture](#-tech-stack--architecture)
- [Quick start](#-quick-start)
- [Docker deployment](#-docker-deployment)
- [Key configuration](#-key-configuration)
- [Benchmarks](#-benchmarks)
- [Roadmap](#-roadmap)
- [Documentation](#-documentation)
- [Contributing & community](#-contributing--community)
- [License](#-license)

---

## Why ShangShu AI

Most AI application frameworks are Python-first. Java teams either import a foreign stack or hand-assemble scattered components. ShangShu AI wires the whole AI application surface into **one pure-Java engineering system**:

- **Spring AI native**: model access, embeddings and vector stores all go through Spring AI abstractions. The embedding backend switches between local Ollama and Alibaba DashScope with a single flag (both 768-dim; run `reembed.py` after switching).
- **A RAG pipeline you can actually measure**: QueryRewrite → vector / keyword / parent-doc / knowledge-graph recall → RRF fusion → rerank → citations. **Recall@5 0.960 with a 96% hit rate on a 50-question benchmark**, all reproducible from the repo.
- **Production plumbing included**: Flyway migrations, JWT + RBAC + CSRF + XSS, RabbitMQ async processing, Redis Stack sessions, Prometheus metrics, one-command Docker Compose.
- **Consistent multi-client experience**: Vue 3 console, Android (Compose), uni-app mini program.

> The web app opens on a public landing page (platform overview, capability list, repo links) before login.

---

## ✨ Features

| Module | Capabilities |
| --- | --- |
| **Model gateway** | DeepSeek / Qwen / Ollama behind one registry (`sys_model_config`), hot-rebuilt clients, connectivity tests, routing and fallback |
| **Knowledge-base RAG** | Parsing and chunking, multi-channel recall, RRF, rerank, feedback loop, Redis hit cache, step-by-step retrieval debugging |
| **Agent** | ReAct and Function-Calling engines, tool registry, execution tracing |
| **Visual workflows** | 20 node executors, Dify-style canvas (minimap, custom edges), DAG execution, SSE streaming, per-node retry |
| **Customer service** | Intent recognition, slot filling, clarification templates, state machine, KB fallback |
| **Course Q&A** | RAG scenario: QueryRewrite + multi-channel retrieval + citations |
| **Security** | JWT dual tokens, CSRF double-submit, XSS filter, RBAC, Jasypt field encryption |
| **Observability** | End-to-end TraceId, conversation logs, LLM call stats, Prometheus, Actuator |
| **Clients** | Vue 3 console · Android (Kotlin + Compose) · uni-app mini program |

<details>
<summary><b>Workflow node catalogue (20 types)</b></summary>

`Start` · `End` · `LLM` · `IfElse` · `Loop` · `Iteration` · `KnowledgeRetrieval` · `QuestionClassifier` · `Agent` · `Code` · `HttpRequest` · `Tool` · `HumanInput` · `VariableAssigner` · `VariableAggregator` · `TemplateTransform` · `ParameterExtractor` · `ListFilter` · `DocExtractor` · `Answer`

See [docs/workflow-nodes.md](docs/workflow-nodes.md) for usage and [docs/workflow-node-spi.md](docs/workflow-node-spi.md) for extending.

</details>

---

## 📸 Screenshots

### Knowledge-base RAG

![Knowledge-base RAG](docs/screenshots/knowledge-base-rag.png)

> Retrieval results unfold step by step: original query, rewritten queries, per-channel latency, RRF fusion, rerank scores and the final citations.

---

## 🏗 Tech stack & architecture

| Layer | Technology |
| --- | --- |
| Backend | Java 21 · Spring Boot 4.1 · Spring AI 2.0 · Spring Security 7 · MyBatis-Plus 3.5 · Flyway · Redisson 4 |
| Middleware | MySQL 8 · Redis 7 Stack (RediSearch) · Elasticsearch 9 · RabbitMQ 3.13 |
| Web | Vue 3 · TypeScript · Vite · Element Plus · Pinia · Vue Flow · ECharts |
| Mobile | Kotlin · Jetpack Compose · Retrofit |
| Infra | Docker Compose · Nginx · p6spy · Spotless · Checkstyle · GitHub Actions |

> **Version note**: Spring Boot 4.1 / Spring AI 2.0 are milestone releases; ES 9 is aligned with the Spring Data Elasticsearch 6.0 client; Redis Stack ships RediSearch (`FT._LIST` is required by Spring AI ChatMemory).

```
ai/
├── api/                       # Backend (Spring Boot + Spring AI)
│   └── src/main/java/com/ai/api/
│       ├── agent/             # Agent runtime: ReAct / FC engines, tools, tracing
│       ├── workflow/          # Workflow engine: 20 NodeExecutors, DAG runner
│       ├── knowledge/         # RAG: parsing, chunking, recall, RRF, rerank
│       ├── chat/              # Chat: SSE streaming, sessions, ES index
│       ├── cs/                # Customer service: intents, slots, state machine
│       ├── model/             # Model registry: hot loading, connectivity
│       ├── identity/          # JWT, OAuth2, RBAC, API keys
│       ├── data/              # MyBatis-Plus, RabbitMQ, Redis
│       ├── audit/ · common/   # Audit, unified response, TraceId, CORS
│       └── ...                # See api/AGENTS.md
├── ai-agent-web/              # Web console (Vue 3 + Element Plus + Vue Flow)
├── ai-agent-client/           # Android (Kotlin + Compose)
├── ai-agent-mini/             # Mini program (uni-app)
├── dev/                       # Evaluation & corpus tooling (datasets, fetch, re-embed, calibration)
├── docs/                      # Architecture, benchmark reports, dev logs, templates
├── docker-compose.yml         # Middleware / API / Web orchestration
└── .env.example               # Environment template
```

---

## 🚀 Quick start

**Requirements**: Docker + JDK 21 + Node 22+ + Maven 3.9+

```bash
# 1. Start middleware (MySQL / Redis Stack / ES / RabbitMQ)
docker compose up -d

# 2. Start the backend (zero config: application-dev.yml matches Compose defaults)
cd api
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# Swagger:  http://localhost:8080/swagger-ui.html
# Health:   http://localhost:8080/actuator/health

# 3. Start the web console
cd ai-agent-web
npm install
npm run dev
# Open: http://localhost:5173
```

**Smoke test**:

- `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`
- `http://localhost:5173` shows the landing page; sign up and enter the dashboard
- Create a knowledge base, upload a Markdown file, and watch each retrieval stage in the "retrieval test" panel

> **Port conflict**: if host port 3306 is taken, run `MYSQL_HOST_PORT=3307 docker compose up -d` and start the backend with `MYSQL_PORT=3307`.

---

## 🐳 Docker deployment

```bash
cp .env.example .env
# A full deployment requires at least AI_AUTH_JWT_SECRET and JASYPT_ENCRYPTOR_PASSWORD

docker compose --profile full up -d --build
# API: http://localhost:8080
# Web: http://localhost
```

| Command | Services |
| --- | --- |
| `docker compose up -d` | MySQL · Redis Stack · Elasticsearch · RabbitMQ (middleware only) |
| `docker compose --profile full up -d --build` | middleware + API + Web |

> Before going to production, change `AI_AUTH_JWT_SECRET` (≥32 random chars), `JASYPT_ENCRYPTOR_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD`, `REDIS_PASSWORD`, `RABBITMQ_PASSWORD`, and provide at least one model API key.

---

## ⚙️ Key configuration

### Models

Models live in the `sys_model_config` table (provider / model_id / base_url / api_key). `ChatClientFactory` hot-builds a client per model id; `api_key` supports both the encrypted column (`api_key_encrypted`, Jasypt) and the plain column.

### Embedding & rerank

```yaml
rag:
  embedding:
    provider: dashscope   # dashscope | ollama
  rerank:
    enabled: true
    protocol: dashscope
    model: qwen3.7-text-rerank
```

Key resolution order: `DASHSCOPE_API_KEY` env → `api/src/main/resources/application-secret.yml` (gitignored) → empty.

> Fully offline: set `RAG_EMBEDDING_PROVIDER=ollama` and `ollama pull nomic-embed-text`; both are 768-dim, but re-embed all chunks with `dev/rag-eval/reembed.py` after switching.

### Knowledge-base hit gate

`cs.kb-hit-threshold` defaults to **0.4**, derived from a 215-question rerank calibration (see [rerank-calibration-report.md](docs/platform/testing/rerank-calibration-report.md)).

---

## 📊 Benchmarks

Every number below is reproducible: datasets, scripts and reports live in `dev/` and `docs/platform/testing/`.

### Corpus

| Knowledge base | Docs | Chunks | Tokens | Source |
| --- | --- | --- | --- | --- |
| kb1 benchmark | 2 | 70 | 12,486 | Compiler-principles lab report |
| kb3 tech docs | 27 | 338 | 56,170 | Project's own docs |
| kb4 official docs | 33 | 747 | 122,968 | Spring AI / MyBatis-Plus / Elasticsearch / RabbitMQ repos |
| kb5 Chinese docs | 193 | 2,024 | 353,565 | Kubernetes official docs (CC BY 4.0) |
| **Total** | **255** | **3,179** | **545,189** | MySQL and ES counts reconcile |

### Retrieval quality (kb1 · 50 questions)

| Config | Recall@5 | MRR | nDCG@5 | Hit rate | Rerank tokens per query |
| --- | --- | --- | --- | --- | --- |
| RRF only | 0.780 | 0.491 | 0.563 | — | 0 |
| **+ Rerank (800-char truncation)** | **0.960** | **0.857** | **0.884** | **96%** | **5,210** |

> 800-char truncation saves 32% tokens while improving metrics over 1,200 chars (0.940 → 0.960). See [rag-eval-slim-report.md](docs/platform/testing/rag-eval-slim-report.md).

### Rerank calibration (215 independent questions)

| Gate threshold | Positive kept | Negative leaked |
| --- | --- | --- |
| 0.3 | 85.5–87.5% | 5.6–6.6% |
| **0.4 (default)** | **80–84%** | **2.1–2.8%** |
| 0.5 | 74.5–75.0% | 0.6–1.0% |
| 0.6 | 61.8–63.7% | 0–0.3% |

> Top-1 accuracy of the positive passage: 92.5% with 5 candidates, 91.2% with 10, 87.3% with 15. A 0.5 gate silently drops ~1/4 of positives — the real cause behind the early "rerank hurts Chinese corpus" result. See [rerank-calibration-report.md](docs/platform/testing/rerank-calibration-report.md).

### Corpus enrichment

All 255 documents now carry a summary, 5 keywords and 3 Q&A pairs (1,275 keywords, 765 QA pairs), generated offline and backfilled into the database; `qa_json` can seed evaluation sets. See [corpus-enrichment-report.md](docs/platform/testing/corpus-enrichment-report.md).

---

## 🗺 Roadmap

- [x] Multi-model gateway with hot loading and connectivity checks
- [x] Full RAG pipeline (QueryRewrite / multi-channel recall / RRF / rerank / citations)
- [x] RAG evaluation harness (datasets + Recall/MRR/nDCG reports + gate calibration)
- [x] Visual workflow engine (20 node executors + DAG + SSE)
- [x] Agent ReAct / Function-Calling engines
- [x] App publishing with unified API keys (OpenAI-compatible)
- [x] Workflow run persistence + DAG execution view
- [x] Prompt versioning · workflow versioning · node retry · parallel branches
- [x] Corpus enrichment (summary / keywords / Q&A) surfaced in the doc drawer
- [ ] Multimodal corpus: diagram captioning (VL) for image-aware retrieval
- [ ] Voice pipeline: STT / TTS (`VoiceService` already exposes OpenAI-compatible endpoints)
- [ ] Dark mode for the web console (GFI-3)
- [ ] Knowledge-graph recall enhancements and incremental indexing
- [ ] Raise unit-test coverage to 60%

---

## 📚 Documentation

Most documents are written in Chinese.

| Document | Path |
| --- | --- |
| Architecture | [docs/platform/architecture.md](docs/platform/architecture.md) |
| Corpus statistics | [docs/platform/testing/knowledge-base-corpus.md](docs/platform/testing/knowledge-base-corpus.md) |
| RAG benchmark (50 Q) | [docs/platform/testing/rag-eval-slim-report.md](docs/platform/testing/rag-eval-slim-report.md) |
| Chinese corpus eval (80 Q) | [docs/platform/testing/rag-eval-kb5-report.md](docs/platform/testing/rag-eval-kb5-report.md) |
| Rerank calibration | [docs/platform/testing/rerank-calibration-report.md](docs/platform/testing/rerank-calibration-report.md) |
| Corpus enrichment | [docs/platform/testing/corpus-enrichment-report.md](docs/platform/testing/corpus-enrichment-report.md) |
| Workflow nodes / SPI | [docs/workflow-nodes.md](docs/workflow-nodes.md) · [docs/workflow-node-spi.md](docs/workflow-node-spi.md) |

---

## 🤝 Contributing & community

- **Contributing**: [CONTRIBUTING.md](CONTRIBUTING.md) (setup · conventions · PR flow)
- **Good first issues**: [docs/good-first-issues.md](docs/good-first-issues.md)
- **Security**: report privately via [SECURITY.md](SECURITY.md)
- **Release notes**: [docs/release-notes/](docs/release-notes/) (currently v0.1.0)
- **Discussions**: [GitHub Discussions](https://github.com/jiangjiangbozan/ShangShu-AI/discussions)

If this project helps you, a ⭐ Star is appreciated.

---

## License

[Apache License 2.0](LICENSE)

---

## ⭐ Star History

<a href="https://star-history.com/#jiangjiangbozan/ShangShu-AI&Date">
  <img src="https://api.star-history.com/svg?repos=jiangjiangbozan/ShangShu-AI&type=Date" alt="Star History Chart" width="600" />
</a>
