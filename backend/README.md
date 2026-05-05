# VoiceTask Backend

Node.js + Fastify + TypeScript + Supabase backend for voice-to-task conversion.

## Stack

- **Fastify 4.x** — HTTP framework
- **TypeScript** — Type safety (strict mode)
- **Supabase** — Auth + Postgres database (no ORM)
- **Zod** — Schema validation
- **OpenAI SDK** — Whisper (transcription) + GPT-4o-mini (task extraction)
- **Firebase Admin SDK** — FCM push notifications
- **@upstash/qstash** — Scheduled task reminders
- **jsonwebtoken** — Supabase JWT verification
- **Pino** — Structured logging

## Architecture

```
┌─────────────────┐
│   Android App   │
└────────┬────────┘
         │ REST API (JWT auth)
         ▼
┌─────────────────────────────────────┐
│      Fastify Backend (Node.js)      │
├─────────────────────────────────────┤
│ ├─ /users/me (POST/PATCH)           │
│ ├─ /voice/transcribe (POST)         │
│ ├─ /voice/extract (POST)            │
│ ├─ /tasks (GET/POST/PATCH/DELETE)   │
│ ├─ /devices (POST/DELETE)           │
│ └─ /webhooks/qstash (POST)          │
└───┬──────┬──────┬──────────────────┘
    │      │      │
    ▼      ▼      ▼
┌────────┬───────────┬──────────────┐
│Supabase│ OpenAI   │ Firebase     │
│ Auth   │ (Whisper)│ (FCM Push)   │
│ +DB    │ (GPT-4o) │              │
└────────┴──────────┴──────────────┘
          │
          ▼
      ┌────────────┐
      │ Upstash    │
      │ QStash     │
      │ (Schedule) │
      └────────────┘
```

## Quick Start

### 1. Prerequisites

- **Supabase project** – [Create here](https://supabase.com)
- **OpenAI API key** – [Get here](https://platform.openai.com/api-keys)
- **Firebase project** – [Create here](https://firebase.google.com)
- **Upstash QStash** – [Create here](https://upstash.com)
- **Node.js 18+** and **npm**

### 2. Setup Database Tables

See **SETUP.md** for detailed Supabase table creation instructions.

### 3. Environment Variables

```bash
cp .env.example .env
# Edit .env with your credentials
```

### 4. Install & Run

```bash
npm install
npm run dev
```

Server starts at `http://localhost:3000`

### 5. Verify

```bash
curl http://localhost:3000/health
```

Expected response:
```json
{
  "status": "ok",
  "timestamp": "2026-05-03T12:00:00.000Z"
}
```

## Project Structure

```
src/
├── server.ts              # Fastify app setup
├── config/
│   ├── env.ts            # Zod-validated environment variables
│   └── supabase.ts       # Supabase client singleton
├── middleware/
│   └── auth.ts           # JWT verification + request.user attachment
├── routes/
│   ├── users.ts          # POST/PATCH /users/me (JIT user provisioning)
│   ├── voice.ts          # POST /voice/transcribe, POST /voice/extract
│   ├── tasks.ts          # CRUD /tasks with QStash scheduling
│   ├── devices.ts        # POST/DELETE /devices (FCM token registration)
│   └── webhooks.ts       # POST /webhooks/qstash (signature verification + FCM)
├── services/
│   ├── openai.ts         # transcribeAudio(), extractTaskDetails()
│   ├── fcm.ts            # sendTaskReminderNotifications()
│   ├── qstash.ts         # scheduleTaskReminder(), cancelTaskReminder()
│   └── supabaseJwt.ts    # JWT verification helpers
├── schemas/
│   └── task.ts           # Zod request/response schemas
└── utils/
    └── errors.ts         # Error handling
```

## API Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/health` | ❌ | Health check |
| POST | `/users/me` | ✅ | Create/update user |
| PATCH | `/users/me` | ✅ | Update user profile |
| GET | `/tasks` | ✅ | List tasks (optional `?status=PENDING`) |
| GET | `/tasks/:id` | ✅ | Get task by ID |
| POST | `/tasks` | ✅ | Create task |
| PATCH | `/tasks/:id` | ✅ | Update task |
| DELETE | `/tasks/:id` | ✅ | Delete task |
| POST | `/devices` | ✅ | Register FCM device token |
| DELETE | `/devices/:fcmToken` | ✅ | Unregister device |
| POST | `/voice/transcribe` | ✅ | Transcribe audio file (M4A) |
| POST | `/voice/extract` | ✅ | Extract task from text |
| POST | `/webhooks/qstash` | ❌* | QStash reminder webhook |

*QStash webhook uses signature verification, not JWT.

## Development

### Build

```bash
npm run build
```

Compiles TypeScript to `dist/` using strict mode.

### Dev Server with Hot Reload

```bash
npm run dev
```

Runs `ts-node` with file watch.

### Production Build & Run

```bash
npm run build
npm start
```

Runs compiled `dist/server.js` directly.

## API Examples

### Create Task from Voice

```bash
# Step 1: Transcribe audio
curl -X POST http://localhost:3000/voice/transcribe \
  -H "Authorization: Bearer <JWT>" \
  -F "audio=@recording.m4a"
# → { "transcript": "Call mom tomorrow at 6pm" }

# Step 2: Extract task details
curl -X POST http://localhost:3000/voice/extract \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Call mom tomorrow at 6pm",
    "timezone": "Asia/Kolkata"
  }'
# → { "title": "Call mom", "dueAt": "2026-05-04T12:30:00Z", ... }

# Step 3: Create task
curl -X POST http://localhost:3000/tasks \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Call mom",
    "dueAt": "2026-05-04T12:30:00Z",
    "reminderOffsetMinutes": 15,
    "priority": "HIGH"
  }'
# → { "id": "uuid", "title": "Call mom", ... }
```

### Get All Tasks

```bash
curl http://localhost:3000/tasks \
  -H "Authorization: Bearer <JWT>"
```

Filter by status:
```bash
curl "http://localhost:3000/tasks?status=PENDING" \
  -H "Authorization: Bearer <JWT>"
```

## Configuration

All configuration via environment variables (see `.env.example`).

### Key Variables

- `SUPABASE_URL` — Supabase project URL
- `SUPABASE_SERVICE_ROLE_KEY` — Service account key (server-only)
- `SUPABASE_JWT_SECRET` — JWT signing secret for token verification
- `OPENAI_API_KEY` — OpenAI API key
- `FIREBASE_SERVICE_ACCOUNT_JSON` — Firebase service account (single-line JSON)
- `QSTASH_TOKEN` — Upstash QStash API token
- `BACKEND_PUBLIC_URL` — Public backend URL (for QStash webhooks)
- `PORT` — Server port (default: 3000)
- `NODE_ENV` — `development` or `production`

## Deployment

### Docker

Build image:
```bash
docker build -t voicetask-backend .
```

Run:
```bash
docker run -p 3000:3000 \
  -e SUPABASE_URL=... \
  -e SUPABASE_SERVICE_ROLE_KEY=... \
  ... \
  voicetask-backend
```

### Fly.io

See **SETUP.md** for Fly.io deployment instructions.

## Logging

Logs output via Pino (structured JSON in production, pretty-printed in dev).

Control verbosity via `LOG_LEVEL` env var:
```bash
# Errors only
LOG_LEVEL=error npm run dev

# Everything including traces
LOG_LEVEL=trace npm run dev
```

## Error Handling

Consistent error response format:
```json
{
  "error": "Task not found",
  "code": "NOT_FOUND"
}
```

Status codes:
- **401** — `UNAUTHORIZED` (invalid/missing JWT)
- **403** — `FORBIDDEN` (user doesn't own resource)
- **404** — `NOT_FOUND` (resource not found)
- **400** — `BAD_REQUEST` (invalid input)
- **413** — `PAYLOAD_TOO_LARGE` (file > 10 MB)
- **500** — `INTERNAL_SERVER_ERROR`

## Security Notes

- Audio files are **never persisted** — discarded after OpenAI transcription
- All timestamps are UTC; timezone conversion happens in LLM
- File upload limit is **10 MB**
- All DB queries filter by `user_id` — no cross-user access possible
- QStash webhooks are signature-verified
- JWT tokens must have `aud="authenticated"` claim

## Troubleshooting

### Build fails with TS errors
```bash
rm -rf node_modules dist
npm install
npm run build
```

### Port 3000 already in use
```bash
lsof -i :3000
kill -9 <PID>
# Or use a different port:
PORT=3001 npm run dev
```

### Supabase connection errors
- Verify `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are correct
- Check database tables exist (see SETUP.md)
- For network issues, check firewall/security groups

### OpenAI API errors
- Verify API key is valid and billing is enabled
- Check rate limits (esp. if testing heavily)
- Ensure `whisper-1` and `gpt-4o-mini` models are available

### Firebase FCM fails
- Verify `FIREBASE_SERVICE_ACCOUNT_JSON` is valid
- Check Cloud Messaging is enabled in Firebase Console
- Ensure device token is registered via `POST /devices` first

## Type Safety

TypeScript strict mode is enabled. Before committing:
```bash
npm run build  # Must pass with zero errors
```

## Next Steps

1. See **SETUP.md** for detailed setup instructions
2. Set up Android client (see `../android/README.md`)
3. Create tasks and receive push notifications
4. Deploy to production (Fly.io, Railway, etc.)
