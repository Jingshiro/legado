# AGENTS.md — Legado (阅读) Development Guide

## Project Overview

Legado is an Android reading app (Kotlin) with a Vue 3 web frontend. Fork from gedoor/legado via Luoyacheng/legado (Sigma).

**Package**: `io.legado.app`
**Min SDK**: 21 (Android 5.0)
**Target SDK**: 36
**JDK**: 17

## Repository Structure

```
app/                    # Main Android app (Kotlin)
modules/
  book/                 # Book parsing library
  rhino/                # Rhino JS engine wrapper
  web/                  # Vue 3 + TypeScript frontend (pnpm + Vite)
```

## Build Commands

### Android (Gradle)

```bash
# Debug APK
./gradlew assembleAppDebug

# Release APK (requires signing config)
./gradlew assembleAppRelease

# Compile Kotlin only (faster iteration)
./gradlew :app:compileAppDebugKotlin

# KSP processing (Room, Glide)
./gradlew kspAppDebugKotlin

# Clean
./gradlew clean

# Stop daemon
./gradlew --stop
```

**Windows**: Use `gradlew.bat` instead of `./gradlew`

### Web Module (modules/web)

```bash
cd modules/web
pnpm install
pnpm dev        # Dev server at http://localhost:8080
pnpm build      # Build + sync to app/src/main/assets/web/vue/
pnpm type-check # Vue type checking
pnpm lint:fix   # ESLint auto-fix
pnpm format     # Prettier format
```

**Requires**: Node.js >= 20, pnpm >= 9

## Architecture Notes

### Android App (`app/src/main/java/io/legado/app/`)

- `api/` — Web API controllers
- `base/` — Base classes
- `data/` — Room database (dao/, entities/)
- `model/` — Parsers (analyzeRule, localBook, rss, webBook)
- `ui/` — Activities/Fragments (see ui/README.md for layout)
- `web/` — HTTP/WebSocket servers (port 1122/1123)
- `service/` — Android services (audio, TTS, web, download)

### Database (Room + KSP)

- Schema exports to `app/schemas/`
- KSP args configured in `app/build.gradle` (room.incremental, room.expandProjection)
- Entity classes in `data/entities/`

### Web Frontend

- Vue 3 + Element Plus + Pinia + Vue Router
- Auto-imports via unplugin (components, composables)
- Build output syncs to Android assets via `scripts/sync.js` (CI only)
- Routes: `/` (bookshelf), `/#/bookSource`, `/#/rssSource`

## Version Scheme

```groovy
version = "3." + releaseTime()  // e.g., "3.26.070914"
// Format: 3.<2-digit-year>.<month><day><hour> (GMT+8)
```

Version code: `10000 + git commit count`

## Signing Configuration

**Option 1**: `keystore.properties` in project root (gitignored)
```properties
storeFile=path/to/keystore.jks
storePassword=xxx
keyAlias=xxx
keyPassword=xxx
```

**Option 2**: Gradle properties (`RELEASE_STORE_FILE`, etc.)

## Dependency Constraints (DO NOT UPDATE)

These versions are pinned due to breaking changes:

| Library | Version | Reason |
|---------|---------|--------|
| jsoup | 1.16.2 | Breaking change in newer versions (see issue #3811) |
| commons-text | 1.13.1 | Uses Arrays.setAll (crashes on Android < 6) |
| rhino | 1.8.1 | Uses VarHandle.compareAndExchange (won't compile on Android < 8) |
| hutool | 5.8.22 | Pinned |

## Cronet Native Libraries

Downloaded separately via `app/download.gradle`:
```bash
./gradlew app:downloadCronet  # Update when changing CronetVersion in gradle.properties
```

## CI/CD

GitHub Actions workflow (`.github/workflows/build_and_release.yml`):
- Triggers on push to `main`
- Builds two release variants: `release` (beta) and `releaseS` (formal)
- Uploads APKs to Cloudflare R2
- Generates `latest.json` for app update checks

## API Documentation

- `LEGADO_WEB_API.md` — Comprehensive web service API reference (705 lines)
- `api.md` — Original API docs
- Web service endpoints: `http://<device-ip>:1122`
- WebSocket: `ws://<device-ip>:1123`

## Testing

- Unit tests: JUnit 4
- Android tests: Espresso + AndroidX Test
- No web module tests configured

## Commit Convention

Uses commitizen with conventional changelog:
```bash
npx cz  # Interactive commit
```

## Common Pitfalls

1. **Gradle daemon memory**: Configured for 6GB (`-Xmx6g`). If OOM, increase in `gradle.properties`
2. **Configuration cache**: Disabled (`org.gradle.unsafe.configuration-cache=false`)
3. **Resource optimization**: Enabled (`android.enableResourceOptimizations=true`)
4. **Non-transitive R classes**: Enabled (`android.nonTransitiveRClass=true`)
5. **Web build sync**: `scripts/sync.js` only runs in GitHub CI. For local dev, manually copy `modules/web/dist/` to `app/src/main/assets/web/vue/` if needed
6. **Mirror repos**: Commented out in `settings.gradle`. Enable if Google/Maven Central unreachable
7. **Firebase**: Requires `google-services.json` in `app/` (present in repo)
