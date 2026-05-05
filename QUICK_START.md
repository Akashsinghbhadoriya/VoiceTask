# VoiceTask — Quick Start Guide

Get the VoiceTask app running in 30 minutes (after gathering credentials).

## Prerequisites Checklist

Before you start, have these ready:

- [ ] **Supabase Project** — URL, Anon Key, JWT Secret, Postgres Connection String
- [ ] **Google OAuth** — Web Client ID + Android Client ID (with SHA-1)
- [ ] **OpenAI API Key** — with billing enabled
- [ ] **Firebase** — `google-services.json` + Service Account JSON
- [ ] **Upstash QStash** — Token + Signing Keys (2 keys)
- [ ] **Tools** — Docker, Node.js 20+, Java 17, Android Studio

## Backend — 5 Minutes

```bash
cd backend

# 1. Install dependencies
npm install

# 2. Create .env from template
cp .env.example .env

# 3. Fill in your credentials in .env
# - DATABASE_URL (Supabase connection string)
# - SUPABASE_JWT_SECRET
# - OPENAI_API_KEY
# - FIREBASE_SERVICE_ACCOUNT_JSON
# - QSTASH_TOKEN, QSTASH_CURRENT_SIGNING_KEY, QSTASH_NEXT_SIGNING_KEY

# 4. Start Postgres (Docker)
docker compose up -d

# 5. Initialize database
npx prisma migrate dev --name init

# 6. Start server
npm run dev
# → Running on http://localhost:3000

# 7. Test health endpoint
curl http://localhost:3000/health
# → { "status": "ok", "timestamp": "..." }
```

**Done!** Backend is running.

---

## Android — 10 Minutes

```bash
cd android

# 1. Update BuildConfig fields
# Edit app/build.gradle.kts and set:
# - BACKEND_URL = "http://10.0.2.2:3000" (for emulator) or your backend URL
# - SUPABASE_URL = "https://your-project.supabase.co"
# - SUPABASE_ANON_KEY = "your-anon-key"
# - GOOGLE_WEB_CLIENT_ID = "your-web-client-id.apps.googleusercontent.com"

# 2. Place google-services.json
cp ~/Downloads/google-services.json app/

# 3. Build and run
./gradlew installDebug

# Or in Android Studio:
# - Open project
# - Select emulator or device
# - Run → Run 'app'
```

**Done!** App is installed and ready to test.

---

## End-to-End Test — 5 Minutes

1. **Launch app** → Google Sign-In screen appears
2. **Sign in with Google** (test account is fine)
3. **Home screen** → FAB (+) at bottom right
4. **Tap FAB** → Record screen
5. **Hold record button** → Speak: "Call mom tomorrow at 6pm"
6. **Release** → App uploads, transcribes, extracts
7. **See transcript** → Tap "Continue"
8. **Preview task** → All fields auto-filled, tap "Save Task"
9. **Back to home** → Task appears in list
10. **Tap task** → Opens detail screen with edit/delete options

**Done!** Full flow works end-to-end.

---

## Test Push Notifications (Optional, Requires ngrok)

If you want to test the reminder notification:

```bash
# 1. Install ngrok
brew install ngrok

# 2. Expose your backend
ngrok http 3000
# → Copy the URL, e.g., https://abcd-1234.ngrok.io

# 3. Update backend .env
# Set: BACKEND_PUBLIC_URL=https://abcd-1234.ngrok.io

# 4. Restart backend
npm run dev

# 5. In app, create a task with due date 2 minutes from now
# - Home → Record → Say "Test reminder"
# - Continue → Set Due At to 2 minutes from now
# - Save Task

# 6. Wait 2 minutes → Notification appears on device
```

---

## Deployment Checklist

When ready for production:

### Backend
- [ ] Database: Use Supabase Postgres, not Docker Postgres
- [ ] Secrets: All `.env` values from Supabase, OpenAI, Firebase, Upstash
- [ ] Build: `npm run build` succeeds
- [ ] Deploy: Push to Fly.io/Railway/Heroku
- [ ] Test: `curl https://your-api.com/health` returns 200

### Android
- [ ] BuildConfig: Update to production backend URL
- [ ] Signing: Create keystore and sign release APK
- [ ] Build: `./gradlew bundleRelease` (creates AAB for Play Store)
- [ ] Submit: Upload to Google Play Console

---

## Common Issues

### Backend won't start
- Check `.env` is filled in completely
- Verify `DATABASE_URL` is correct
- Ensure Postgres is running: `docker compose ps`
- Check logs: `npm run dev` shows errors in console

### Android won't sign in
- Verify `SUPABASE_URL` and `SUPABASE_ANON_KEY` are correct
- Check `GOOGLE_WEB_CLIENT_ID` matches Google Cloud project
- Ensure Supabase Google provider is enabled + credentials added

### Audio upload fails
- Backend must be reachable: `BACKEND_URL` in BuildConfig
- For emulator: use `http://10.0.2.2:3000` (not `localhost`)
- For device: use real backend URL

### Push notifications don't arrive
- Verify FCM token is registered: check logcat for "New FCM token"
- Ensure `google-services.json` is in correct location
- Backend service account must be valid

See full troubleshooting in:
- `backend/README.md`
- `android/README.md`

---

## Architecture Overview

```
Android App
    ↓ (Supabase JWT)
    ↓ (HTTPS)
Node.js Backend
    ↓ (OpenAI, Firebase, QStash)
Supabase Postgres + External APIs
```

All timestamps in UTC. Android converts for display.  
Audio never persisted. Only confirmed transcripts saved.

---

## Important Files

| Path | Purpose |
|------|---------|
| `backend/.env.example` | Template for backend secrets |
| `backend/requests.http` | Example API calls |
| `android/app/build.gradle.kts` | BuildConfig fields |
| `android/app/google-services.json` | Firebase credentials |
| `README.md` | Full documentation |
| `IMPLEMENTATION_COMPLETE.md` | Feature list + architecture |

---

## Next Commands

**Backend:**
```bash
cd backend
npm run dev  # Start dev server
npm run build  # Compile TypeScript
npx prisma studio  # Browse database
```

**Android:**
```bash
cd android
./gradlew build  # Build APK
./gradlew installDebug  # Install to device
./gradlew test  # Run tests (when added)
```

---

**Time to First Feature:** 10-15 minutes  
**Time to Full Stack Running:** 20-30 minutes  
**Time to Production:** 1-2 hours (with credential setup)

Good luck! 🚀
