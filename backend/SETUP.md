# VoiceTask Backend Setup

This backend uses **Fastify** + **TypeScript** + **Supabase** (no ORM/migrations).

## Prerequisites

Before starting, ensure you have:

1. **Supabase Project** – Create at [supabase.com](https://supabase.com)
2. **OpenAI API Key** – Get from [openai.com](https://openai.com/api/)
3. **Firebase Project** – Create at [firebase.google.com](https://firebase.google.com)
4. **Upstash QStash** – Create at [upstash.com](https://upstash.com)
5. **Node.js 18+** and **npm**

---

## 1. Supabase Setup

### 1.1 Create Tables

Log into your Supabase dashboard → **SQL Editor** → run these queries:

```sql
-- Users table
CREATE TABLE public.users (
  id UUID PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  name TEXT,
  picture TEXT,
  timezone TEXT DEFAULT 'UTC',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Devices table (for FCM push notifications)
CREATE TABLE public.devices (
  fcm_token TEXT PRIMARY KEY,
  user_id UUID REFERENCES public.users(id) ON DELETE CASCADE NOT NULL,
  platform TEXT NOT NULL,
  last_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tasks table
CREATE TABLE public.tasks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES public.users(id) ON DELETE CASCADE NOT NULL,
  title TEXT NOT NULL,
  description TEXT,
  transcript TEXT,
  due_at TIMESTAMP,
  reminder_offset_minutes INTEGER DEFAULT 15,
  status TEXT DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
  priority TEXT DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
  qstash_message_id TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_tasks_user_id ON public.tasks(user_id);
CREATE INDEX idx_tasks_due_at ON public.tasks(due_at);
CREATE INDEX idx_tasks_status ON public.tasks(status);
CREATE INDEX idx_devices_user_id ON public.devices(user_id);
```

### 1.2 Get Credentials

In Supabase Dashboard:

1. **Settings** → **API** → Copy:
   - `Project URL` → `SUPABASE_URL`
   - `Service role secret` → `SUPABASE_SERVICE_ROLE_KEY`
   - `JWT secret` → `SUPABASE_JWT_SECRET`

---

## 2. OpenAI Setup

1. Create an API key at [platform.openai.com/account/api-keys](https://platform.openai.com/account/api-keys)
2. Set `OPENAI_API_KEY` in your `.env`

---

## 3. Firebase Setup

### 3.1 Create Service Account

1. Go to [firebase.google.com](https://firebase.google.com) → select your project
2. **Project Settings** → **Service Accounts** → **Generate New Private Key**
3. Copy the JSON, minify it to a single line, and set as `FIREBASE_SERVICE_ACCOUNT_JSON`

```bash
# Example (must be valid JSON on a single line):
FIREBASE_SERVICE_ACCOUNT_JSON='{"type":"service_account","project_id":"...",..."}'
```

### 3.2 Enable Cloud Messaging

- In Firebase Console → **Cloud Messaging** → Note your **Server API Key** (for Android client setup later)

---

## 4. QStash Setup

1. Create account at [upstash.com](https://upstash.com)
2. Create a QStash channel → get:
   - `QSTASH_TOKEN` – Your API token
   - `QSTASH_CURRENT_SIGNING_KEY` – Current signing key
   - `QSTASH_NEXT_SIGNING_KEY` – Next signing key (for rotation)

---

## 5. Local Development

### 5.1 Install Dependencies

```bash
npm install
```

### 5.2 Create .env

Copy `.env.example` → `.env` and fill in all values:

```bash
cp .env.example .env
```

Then edit `.env` with your credentials.

### 5.3 Run Server

```bash
npm run dev
```

Server starts at `http://localhost:3000`

### 5.4 Health Check

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

---

## 6. Testing the API

Use the examples below with a valid Supabase JWT (from `/auth` in Android app or Supabase Dashboard).

### Sign Up / Create User

```bash
curl -X POST http://localhost:3000/users/me \
  -H "Authorization: Bearer <SUPABASE_JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "timezone": "Asia/Kolkata"
  }'
```

### Upload & Transcribe Audio

```bash
curl -X POST http://localhost:3000/voice/transcribe \
  -H "Authorization: Bearer <SUPABASE_JWT>" \
  -F "audio=@path/to/recording.m4a"
```

### Extract Task Details

```bash
curl -X POST http://localhost:3000/voice/extract \
  -H "Authorization: Bearer <SUPABASE_JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Call mom tomorrow at 6pm",
    "timezone": "Asia/Kolkata"
  }'
```

### Create Task

```bash
curl -X POST http://localhost:3000/tasks \
  -H "Authorization: Bearer <SUPABASE_JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Call mom",
    "dueAt": "2026-05-04T12:30:00Z",
    "reminderOffsetMinutes": 15,
    "priority": "HIGH"
  }'
```

### Get Tasks

```bash
curl http://localhost:3000/tasks \
  -H "Authorization: Bearer <SUPABASE_JWT>"
```

---

## 7. Public URL for Webhooks (Local Testing)

QStash needs a public URL to send webhook callbacks. For local testing:

```bash
# Install ngrok
brew install ngrok

# Start ngrok tunnel
ngrok http 3000
```

Then update `.env`:
```
BACKEND_PUBLIC_URL=https://your-ngrok-url.ngrok.io
```

---

## 8. Production Deployment (Fly.io)

### 8.1 Build Docker Image

```bash
docker build -t voicetask-backend .
```

### 8.2 Deploy to Fly.io

```bash
flyctl launch
flyctl secrets set \
  SUPABASE_URL=... \
  SUPABASE_SERVICE_ROLE_KEY=... \
  SUPABASE_JWT_SECRET=... \
  OPENAI_API_KEY=... \
  FIREBASE_SERVICE_ACCOUNT_JSON=... \
  QSTASH_TOKEN=... \
  QSTASH_CURRENT_SIGNING_KEY=... \
  QSTASH_NEXT_SIGNING_KEY=... \
  BACKEND_PUBLIC_URL=https://your-app.fly.dev

flyctl deploy
```

---

## 9. API Endpoints Summary

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/health` | ❌ | Health check |
| `POST` | `/users/me` | ✅ | Create/update user profile |
| `PATCH` | `/users/me` | ✅ | Update user profile |
| `GET` | `/tasks` | ✅ | List tasks (with optional `?status=PENDING`) |
| `GET` | `/tasks/:id` | ✅ | Get task by ID |
| `POST` | `/tasks` | ✅ | Create task |
| `PATCH` | `/tasks/:id` | ✅ | Update task |
| `DELETE` | `/tasks/:id` | ✅ | Delete task |
| `POST` | `/devices` | ✅ | Register FCM device token |
| `DELETE` | `/devices/:fcmToken` | ✅ | Unregister device |
| `POST` | `/voice/transcribe` | ✅ | Transcribe audio to text |
| `POST` | `/voice/extract` | ✅ | Extract task details from text |
| `POST` | `/webhooks/qstash` | ❌* | QStash reminder webhook (signature verified) |

*QStash webhook is verified by signature, not JWT.

---

## 10. Troubleshooting

### "Cannot find module @prisma/client"
- The project no longer uses Prisma. If you see this, clear node_modules and reinstall: `rm -rf node_modules && npm install`

### "SUPABASE_URL environment variable is required"
- Copy `.env.example` to `.env` and fill in all required values

### "Invalid QStash signature"
- Ensure `QSTASH_CURRENT_SIGNING_KEY` and `QSTASH_NEXT_SIGNING_KEY` are correct in `.env`
- Check that `BACKEND_PUBLIC_URL` is publicly reachable

### "OpenAI API error"
- Verify `OPENAI_API_KEY` has billing enabled
- Check that the API key is not expired

---

## 11. Type Safety

This project uses **TypeScript with strict mode**. Run `npm run build` to check for type errors before deployment.

---

## Next Steps

1. Set up Android client (see `../android/README.md`)
2. Register FCM device tokens from Android app
3. Create tasks and receive push notifications
