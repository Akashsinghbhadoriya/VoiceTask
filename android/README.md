# VoiceTask Android App

Kotlin + Jetpack Compose Android application for voice-to-task conversion.

## Stack

- **Kotlin 2.0+** — Language
- **Jetpack Compose** — Modern UI framework
- **Material 3** — Design system
- **Hilt** — Dependency injection
- **Retrofit + OkHttp** — HTTP client
- **Kotlinx Serialization** — JSON parsing
- **Room** — Local database
- **DataStore** — App preferences
- **Coroutines** — Async programming
- **Supabase Kotlin SDK** — Authentication + session management
- **Firebase Cloud Messaging** — Push notifications
- **Coil** — Image loading
- **Accompanist** — Android API wrappers

## Target

- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)

## Prerequisites

### 1. Android Studio

- Download from [developer.android.com](https://developer.android.com/studio)
- Install SDK API 34 and Android SDK build tools

### 2. Supabase Project

You'll need:
- **Supabase URL** — e.g., `https://your-project.supabase.co`
- **Supabase Anon Key** — public key, safe to include in app
- **Google OAuth enabled** with Android client registered

### 3. Google Cloud Console — OAuth Setup

You need **two** OAuth client types:

#### 3a. Get Your Android SHA-1 Fingerprint (for Google Cloud)

Run one of these commands:

**Method 1 — keytool:**
```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android 2>/dev/null | grep SHA1
```

**Method 2 — Gradle:**
```bash
cd android
./gradlew signingReport | grep SHA1
```

Output looks like: `AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD`

#### 3b. Create Web OAuth Client (for Supabase)

1. Go to [console.cloud.google.com](https://console.cloud.google.com) → **APIs & Services → Credentials**
2. Click **+ Create Credentials → OAuth client ID**
3. Select application type: **Web application**
4. Name: `VoiceTask Web`
5. Authorized redirect URIs: Add your Supabase project's callback URL (you'll get this from Supabase Google provider settings)
6. Click **Create**
7. Copy the **Client ID** and **Client Secret** — you'll need these for Supabase

#### 3c. Create Android OAuth Client (tells Google which APK can use OAuth)

1. In Google Cloud Console → **APIs & Services → Credentials** → Click **+ Create Credentials**
2. Select: **OAuth client ID → Android**
3. Package name: `com.akash.voicetask`
4. SHA-1 certificate fingerprint: paste the value from step 3a
5. Click **Create**

**What goes where:**

| Credential | Google Cloud | Supabase | Android App |
|------------|----------|----------|-------------|
| **Web Client ID** | ✅ | ✅ | ✅ in `build.gradle.kts` |
| **Web Client Secret** | ✅ | ✅ | ❌ |
| **Android Client ID** | ✅ | ❌ | ❌ |
| **SHA-1 fingerprint** | ✅ | ❌ (optional) | ❌ |

### 4. Firebase Project — Cloud Messaging Setup

#### 4a. Create Firebase Project

1. Go to [firebase.google.com](https://firebase.google.com) → **Create a project**
2. Enter project name: `voicetask`
3. **Google Analytics**: Click **Disable** (not needed for V1)
4. Click **Create project**
5. Wait for project creation to complete

#### 4b. Add Android App to Firebase

1. On the Firebase console home page, click the **Android** icon (or **+ Add app → Android**)
2. Enter:
   - **Android package name**: `com.akash.voicetask`
   - **App nickname**: `VoiceTask` (optional)
   - **SHA-1 certificate fingerprint**: paste from step 3a (optional but recommended)
3. Click **Register app**
4. Click **Download google-services.json** and save it to:
   ```
   android/app/google-services.json
   ```
5. Skip the "Add Firebase SDK" steps — we've already added dependencies in `build.gradle.kts`

#### 4c. Get Service Account JSON (for Backend)

1. In Firebase Console → **Project Settings** (gear icon) → **Service Accounts** tab
2. Click **Generate New Private Key**
3. A JSON file downloads — this contains backend credentials
4. Open the file and copy the entire contents (it's one giant JSON object)
5. Store it safely — you'll add it to backend `.env` as `FIREBASE_SERVICE_ACCOUNT_JSON`

**Note:** Keep `google-services.json` in `android/app/` and the service account JSON somewhere safe for the backend.

### 5. Supabase — Enable Google Authentication

1. Go to your Supabase Dashboard → **Authentication → Providers**
2. Find **Google** and click it
3. Toggle **Enable** on
4. Paste your **Google Web Client credentials**:
   - **Client ID**: From Google Cloud Console (Web client)
   - **Client Secret**: From Google Cloud Console (Web client)
5. Supabase will show a **Callback URL** — add this to Google Cloud Console if needed
6. Click **Save**

### 6. Backend

- Backend must be running and reachable
- Get the public URL (e.g., `https://your-api.fly.dev`)
- For local development: use `http://10.0.2.2:3000` (Android emulator bridge to host)

## Project Structure

```
app/src/main/java/com/akash/voicetask/
├── VoiceTaskApp.kt          # @HiltAndroidApp entry
├── MainActivity.kt          # Activity with Compose setContent
├── di/                      # Hilt dependency injection
│   ├── SupabaseModule.kt    # SupabaseClient + Auth
│   ├── NetworkModule.kt     # Retrofit, OkHttp, JSON
│   └── DatabaseModule.kt    # Room database
├── data/
│   ├── remote/              # API layer
│   │   ├── ApiService.kt    # Retrofit endpoints
│   │   ├── AuthInterceptor.kt # JWT injection
│   │   └── dto/             # Data transfer objects
│   ├── local/               # Room database layer
│   │   ├── AppDatabase.kt
│   │   ├── TaskEntity.kt
│   │   └── TaskDao.kt
│   └── repository/          # Business logic, data coordination
│       ├── AuthRepository.kt
│       ├── TaskRepository.kt
│       └── VoiceRepository.kt
├── domain/model/            # Domain models (clean architecture)
│   ├── User.kt
│   ├── Task.kt
│   └── UiState.kt
├── ui/
│   ├── theme/               # Material 3 theme
│   │   ├── Theme.kt
│   │   ├── Color.kt
│   │   └── Type.kt
│   ├── auth/                # Sign-in flow
│   │   ├── SignInScreen.kt
│   │   └── SignInViewModel.kt
│   ├── home/                # Task list
│   │   ├── TaskListScreen.kt
│   │   └── TaskListViewModel.kt
│   ├── record/              # Voice recording + extraction
│   │   ├── RecordScreen.kt
│   │   ├── TranscriptScreen.kt
│   │   ├── PreviewScreen.kt
│   │   └── RecordViewModel.kt
│   ├── detail/              # Task detail + edit
│   │   ├── TaskDetailScreen.kt
│   │   └── TaskDetailViewModel.kt
│   └── components/          # Shared composables
├── audio/
│   └── AudioRecorder.kt     # MediaRecorder wrapper
├── notification/
│   ├── FcmService.kt        # FirebaseMessagingService
│   └── NotificationHelper.kt # Channel setup
├── navigation/
│   └── AppNavigation.kt     # Navigation graph
└── util/
    └── Extensions.kt        # Utility functions

app/src/main/res/
├── values/
│   ├── strings.xml          # App strings
│   ├── themes.xml           # Theme definitions
│   └── colors.xml           # Color palettes
├── xml/
│   └── data_extraction_rules.xml # Network security
└── mipmap/                  # App icons
```

## Installation & Setup

### 1. Clone/Download Project

```bash
cd android
```

### 2. Download google-services.json

```bash
# From Firebase Console: Project Settings → Your Apps → google-services.json
cp ~/Downloads/google-services.json app/
```

### 3. Update BuildConfig Fields

Edit `app/build.gradle.kts` in the `defaultConfig` block and update these values:

```kotlin
buildConfigField("String", "BACKEND_URL", "\"http://10.0.2.2:3000\"")  // Emulator to host
buildConfigField("String", "SUPABASE_URL", "\"https://your-project.supabase.co\"")
buildConfigField("String", "SUPABASE_ANON_KEY", "\"your-anon-key\"")
buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"your-web-client-id.apps.googleusercontent.com\"")
```

**Where to find each value:**

| Field | Where to find it | Example |
|-------|-----------------|---------|
| `BACKEND_URL` | Your backend URL (local: `10.0.2.2:3000`, prod: your deployed URL) | `http://10.0.2.2:3000` or `https://api.voicetask.fly.dev` |
| `SUPABASE_URL` | Supabase Dashboard → **Settings → API → Project URL** | `https://undegzabvxewfxeqsvgm.supabase.co` |
| `SUPABASE_ANON_KEY` | Supabase Dashboard → **Settings → API → Anon/Public Key** | `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...` |
| `GOOGLE_WEB_CLIENT_ID` | Google Cloud Console → **Credentials → Web OAuth Client ID** | `1234567890-abcdefghijklmnop.apps.googleusercontent.com` |

For production builds, update `BACKEND_URL` to your deployed backend (not `10.0.2.2`).

### 4. Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Or run directly to emulator/device
./gradlew installDebug

# In Android Studio: Run → Run 'app'
```

## Key Screens

### SignInScreen
- Centered Google Sign-In button
- Handled via `compose-auth-ui` from Supabase SDK
- On success → navigates to TaskListScreen + calls `POST /users/me`

### TaskListScreen
- Lists user's tasks grouped by due date
- Pull-to-refresh support
- Floating action button to record new task
- Tap task → navigate to detail

### RecordScreen
- Large hold-to-record button with waveform animation
- Timer (red at 50s, auto-stop at 60s)
- Release to upload to `/voice/transcribe`
- On success → navigate to TranscriptScreen

### TranscriptScreen
- Editable multi-line TextField with transcript
- "Re-record" → back to RecordScreen
- "Continue" → call `/voice/extract` → navigate to PreviewScreen

### PreviewScreen
- Title (required), description (optional)
- Due date + time picker (with "No date" toggle)
- Reminder offset dropdown
- Priority chips
- Save → call `POST /tasks`

### TaskDetailScreen
- Read-only by default (with "Edit" button)
- Edit mode: same fields as PreviewScreen
- "Mark Complete", "Delete" buttons
- Original transcript collapsed at bottom

## Permissions

The app requests:
- `RECORD_AUDIO` (when first tapping record)
- `POST_NOTIFICATIONS` (Android 13+, after sign-in)
- `INTERNET` (manifest only)

Use `Accompanist` for permission handling with proper rationale dialogs.

## Local Caching (Room)

- TaskEntity mirrors Task fields from backend
- TaskDao exposes `Flow<List<TaskEntity>>` for reactive updates
- On app launch + pull-to-refresh: `GET /tasks` → replace local cache
- On create/update/delete: optimistic local update → API call → revert on error

## Authentication Flow

1. User taps "Sign in with Google"
2. `rememberSignInWithGoogle()` from `compose-auth-ui` handles OAuth flow
3. Supabase SDK automatically stores session (access + refresh tokens)
4. AuthInterceptor injects JWT into every request
5. On 401: attempt token refresh; if fails → redirect to SignInScreen

## FCM Setup

### Receiving Notifications

`FcmService` extends `FirebaseMessagingService`:

```kotlin
override fun onNewToken(token: String) {
    // Call POST /devices with token
}

override fun onMessageReceived(message: RemoteMessage) {
    // Build notification with NotificationCompat
    // Tap intent → deep link to TaskDetailScreen
}
```

Notification channel created at app launch with high importance (vibration, sound).

### Deep Linking

When user taps notification:

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <data android:scheme="voicetask" android:host="tasks" android:pathPrefix="/detail" />
</intent-filter>
```

Task ID passed via data: `voicetask://tasks/detail?taskId=<id>`

## Audio Recording

`AudioRecorder` wraps `MediaRecorder`:

- Output: `MediaRecorder.OutputFormat.MPEG_4`
- Codec: AAC, 16kHz sample rate, mono
- Bitrate: 32 kbps
- Output file: `cacheDir/recording_<timestamp>.m4a`
- Auto-stop at 60 seconds via `Handler.postDelayed()`
- After successful upload: delete cache file
- On app startup: clean orphaned cache files

## Dependencies

See `app/build.gradle.kts` for all versions. Key libraries:

```kotlin
// Compose + Material
androidx.activity:activity-compose:1.8.0
androidx.compose.material3:material3:1.1.2

// Networking
retrofit2:retrofit:2.10.0
com.squareup.okhttp3:okhttp:4.11.0

// Database
androidx.room:room-ktx:2.6.1

// Supabase
io.github.jan-tennert.supabase:auth-kt:2.3.1
io.github.jan-tennert.supabase:compose-auth:2.3.1

// Firebase
com.google.firebase:firebase-messaging

// DI
com.google.dagger:hilt-android:2.48
```

## ProGuard Rules

See `proguard-rules.pro` for rules to preserve:
- Retrofit types
- Room entities
- Firebase classes
- Supabase SDK
- Kotlinx serialization annotations

Build release APK:

```bash
./gradlew bundleRelease  # For Google Play
./gradlew assembleRelease  # For direct APK
```

## Troubleshooting

### Google Sign-In not working
- Verify SHA-1 is registered in Google Cloud Console and Supabase
- Check that `google-services.json` is in `app/`
- Ensure `GOOGLE_WEB_CLIENT_ID` matches your Web OAuth client ID
- Check Supabase Google provider is enabled

### Backend calls failing with 401
- Verify Supabase session exists via `supabaseClient.auth.currentSessionOrNull()`
- Check JWT is being injected in AuthInterceptor
- Verify backend is verifying JWT correctly with `SUPABASE_JWT_SECRET`

### Push notifications not arriving
- Verify Firebase service account is valid on backend
- Check device is registered via `POST /devices` after sign-in
- Ensure notification channel is created with high importance
- Check logcat for FCM errors

### Audio upload failing
- Verify `RECORD_AUDIO` permission is granted
- Check file size is < 10 MB
- Ensure backend is running and reachable

### Room database not updating
- Verify migrations are run (`AppDatabase.onOpen()`)
- Check TaskDao methods return `Flow<>` for reactive updates
- Use Logcat to debug database operations

## Build Variants

### Debug
- Connects to backend at `http://10.0.2.2:3000` (emulator) or your dev URL
- No ProGuard minification
- Full logging enabled
- Debuggable APK

### Release
- Connects to prod backend URL
- ProGuard minification enabled
- Logging reduced
- Must be signed with your keystore

## Performance Tips

- Use `.then()` continuations on network calls to avoid blocking
- Implement pagination for large task lists (future enhancement)
- Cache profile picture with Coil
- Use LazyColumn for long task lists
- Profile with Android Profiler in Android Studio

## Setup Checklist

Follow these steps in order:

- [ ] **1. Credentials Ready**
  - [ ] Supabase project created + URL copied
  - [ ] Supabase anon key copied
  - [ ] Google Web OAuth client ID copied
  - [ ] Google Web OAuth client secret copied
  - [ ] Firebase project created
  - [ ] `google-services.json` downloaded
  - [ ] Firebase service account JSON saved (for backend)

- [ ] **2. Android App Configuration**
  - [ ] Place `google-services.json` in `android/app/`
  - [ ] Update `build.gradle.kts` with real values (BACKEND_URL, SUPABASE_URL, SUPABASE_ANON_KEY, GOOGLE_WEB_CLIENT_ID)
  - [ ] Enable Google provider in Supabase with Web client credentials

- [ ] **3. Build & Test**
  - [ ] Ensure backend is running: `npm run dev` in `backend/` folder
  - [ ] Sync Gradle: `./gradlew build` (or sync in Android Studio)
  - [ ] Run on emulator/device: `./gradlew installDebug` or use Android Studio "Run"
  - [ ] Test Google sign-in flow
  - [ ] Record a test task and verify it appears in backend
  - [ ] Check that push notification arrives on device (if task due in 2 minutes)

## Debugging

### Logcat Filtering

```bash
# Firebase
adb logcat | grep FirebaseMessaging

# Supabase Auth
adb logcat | grep Supabase

# Retrofit
adb logcat | grep OkHttp
```

### Inspect Network Traffic

Android Studio → Logcat → search for `OkHttp` to see raw HTTP requests/responses.

## Future Enhancements (Post-Phase 7)

- Offline-first with sync queue
- Task categories/labels
- Recurring tasks
- Custom notification sounds
- Dark mode
- Widgets
- Wear OS support
