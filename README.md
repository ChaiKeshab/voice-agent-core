# Voice Agent Core

A minimal **MVP core** for building a real‑time AI **voice agent** (speech → reasoning → speech). This repo focuses on the absolute basics so you can experiment fast. **Not production‑ready.**

* **STT**: `whisper-1`
* **LLM**: `gpt-4o-mini`
* **TTS**: `gpt-4o-mini-tts`
* Two implementations: **Raw HTTP** (*`http` branch*) and **Official OpenAI Java SDK** (*`main` branch*)

> Real‑time features like sockets/WebRTC are intentionally **not included** yet — this is a bare core for speech‑to‑speech.

---

## What’s inside

* 🎤 Accepts raw audio input (recommended: WAV)
* 🧠 Transcription → LLM reasoning
* 🔊 Returns TTS audio bytes (default: WAV)
* 🔌 Two integration modes (HTTP / Java SDK)

**Missing (planned):**

* [ ] WebRTC for true real‑time streaming
* [ ] Bidirectional sockets/partial streaming
* [ ] Auth & rate‑limit layer
* [ ] Robust error handling/retries
* [ ] Prod hardening (observability, backpressure, queueing)

---

## Branches

| Branch | Implementation                   | Notes                                                    |
| ------ | -------------------------------- | -------------------------------------------------------- |
| `main` | **Official OpenAI Java SDK**     | Default branch. Simpler setup.                           |
| `http` | **Raw HTTP** (manual REST calls) | Use if you want to see/build raw request/response flows. |

---

## Quickstart (Docker)

> Easiest path. No local JDK/Maven required.

⚠️ Make sure **Docker Desktop** (or Docker Engine on Linux) is running before you continue.

### 1) Clone

```bash
git clone https://github.com/ChaiKeshab/voice-agent-core.git
cd voice-agent-core
# choose your implementation
# for SDK (default): stay on `main`
# for Raw HTTP: git checkout http
```

### 2) Run

```bash
# build containers and start the app
docker compose up --build
```

The service will be available at **[http://localhost:8080](http://localhost:8080)** (or the port you set).

---

## Alternative: Local setup (no Docker)

### Requirements

* **JDK 17+** (Temurin/Adoptium recommended)
* **Maven 3.9+**
* **FFmpeg (optional)** if you plan to transcode audio server‑side
* **OpenAI API key** with models: `whisper-1`, `gpt-4o-mini`, `gpt-4o-mini-tts`

### Configure

The project uses **`application.properties`** for configuration. Add your OpenAI key and other values there:

```properties
openai.api.key=sk-...
server.port=8080
```

⚠️ **Recommendation:** For better security, update the code to read the OpenAI key from an **environment variable** or a **secure secret manager** rather than hardcoding it into `application.properties`.

---

### Run (Spring Boot)

**With Maven plugin (dev‑friendly):**

```bash
mvn spring-boot:run
```

> If you use the raw HTTP implementation, check out the branch first:

```bash
git checkout http
mvn spring-boot:run
```

---

## API

### POST /api/audio/process

Accepts raw audio bytes, performs STT → LLM → TTS, and returns synthesized audio.

#### Headers

* Content-Type: `application/octet-stream`

#### Body

* Binary audio data (single utterance, preferably `.wav`)

#### Response

* 200 OK with audio bytes (audio/wav by default — controlled by response.audio.format)

#### cURL example (Windows file path, WAV in → WAV out)

```bash
curl --location 'http://localhost:8080/api/audio/process' \
--header 'Content-Type: application/octet-stream' \
--data '@c:/Users/kesha/Downloads/test.wav' \
--output reply.wav
```

> Adjust the file path as needed for your system. `.wav` is recommended for best compatibility.

---

## Notes & Constraints

* This is **not** a real‑time agent yet. There is no socket/WebRTC pipeline or partial streaming.
* Error handling is intentionally minimal.
* No auth layer is provided — place behind your API gateway or add filters.
* Consider request timeouts and max upload size if using long audio.

---

## License

MIT (see `LICENSE`).
