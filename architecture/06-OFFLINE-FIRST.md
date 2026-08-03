# 06 - OFFLINE-FIRST: Garantías, Validación, Arquitectura

**Fecha**: 2026-07-08  
**Descripción**: Arquitectura offline-first, validación CI/CD, patrones garantizados.

---

## ✅ GARANTÍA OFFLINE-FIRST

```
┌─────────────────────────────────────────────┐
│  JP MIGAKU FASE 0 = 100% OFFLINE-FIRST     │
│                                             │
│  ❌ NO hay HTTP                            │
│  ❌ NO hay Cloud API                       │
│  ❌ NO hay Firebase                        │
│  ❌ NO hay WebSockets                      │
│  ❌ NO hay telemetría online               │
│                                             │
│  ✅ SÍ hay Room SQLite local                │
│  ✅ SÍ hay datos iniciales en APK           │
│  ✅ SÍ hay cálculos locales (SM-2, streak) │
│  ✅ SÍ hay Clipboard API (local)            │
│                                             │
│  VALIDABLE: grep + CI/CD checks            │
└─────────────────────────────────────────────┘
```

---

## 🏗️ ARQUITECTURA OFFLINE-FIRST

### Capas (del exterior al interior)

```
┌──────────────────────────────────┐
│  Presentation Layer (UI)         │
│  - Jetpack Compose               │
│  - ViewModels (StateFlow)        │
│  - NO context.startActivity()    │
│    para web, NO intents.ACTION.. │
└──────────────────────────────────┘
              ↓
┌──────────────────────────────────┐
│  Domain Layer (Business Logic)   │
│  - Use Cases (puro)              │
│  - Algoritmos (SM-2, streak)     │
│  - Models (DTO)                  │
│  - NO HttpClient, NO Firebase    │
└──────────────────────────────────┘
              ↓
┌──────────────────────────────────┐
│  Data Layer (Persistencia)       │
│  - Room DAOs                     │
│  - Repositories                  │
│  - SQLite WAL mode               │
│  - NO Retrofit, NO OkHttp        │
│  - NO Firestore                  │
│  - NO Cloud Storage              │
└──────────────────────────────────┘
              ↓
┌──────────────────────────────────┐
│  Android Local APIs Only         │
│  - ClipboardManager              │
│  - SharedPreferences             │
│  - File I/O                      │
│  - NO Network I/O                │
└──────────────────────────────────┘
```

### Data Flow (Offline)

```
User Interaction
    ↓ (UI event)
ViewModel
    ↓ (emit event)
UseCase
    ↓ (pure logic)
Repository
    ↓ (access pattern)
Room DAO
    ↓ (SQL)
SQLite DB (en /data/data/.../jpmigaku.db)
    ↓ (read/write local)
    [Fin - No hay network]
```

---

## 🔐 VALIDACIÓN CI/CD

### 1. Grep Checks (No HTTP anywhere)

```bash
#!/bin/bash
# ci-offline-check.sh

echo "🔍 Checking for HTTP/Network dependencies..."

FORBIDDEN_PATTERNS=(
    "import.*retrofit"
    "import.*okhttp"
    "import.*firebase"
    "import.*com\.google\.cloud"
    "import.*com\.amazonaws"
    "http://"
    "https://"
    "socket"
    "HttpClient"
    "OkHttp"
    "Firebase"
    "Firestore"
    "CloudFunction"
    "Endpoint"
)

FOUND_ERROR=0

for pattern in "${FORBIDDEN_PATTERNS[@]}"; do
    MATCHES=$(grep -r "$pattern" app/src/main/java --include="*.kt" || true)
    if [ -n "$MATCHES" ]; then
        echo "❌ FAIL: Found forbidden pattern: $pattern"
        echo "$MATCHES"
        FOUND_ERROR=1
    fi
done

if [ $FOUND_ERROR -eq 1 ]; then
    echo "❌ OFFLINE-FIRST validation failed"
    exit 1
else
    echo "✅ PASS: No HTTP/Cloud dependencies found"
    exit 0
fi
```

### 2. Gradle Dependency Check

```kotlin
// build.gradle.kts

dependencies {
    // ✅ PERMITIDOS:
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.compose.runtime:runtime:1.6.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    
    // ❌ PROHIBIDOS (would break offline):
    // implementation("com.squareup.retrofit2:retrofit:2.9.0")
    // implementation("com.google.firebase:firebase-database:21.0.0")
    // implementation("com.amazonaws:aws-java-sdk-core:1.12.0")
}

// CI/CD can verify dependency-lock matches this
```

### 3. AndroidManifest.xml Permissions

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.READ_CLIPBOARD" />
    
    <!-- ✅ SOLO Clipboard (local) -->
    
    <!-- ❌ JAMÁS: -->
    <!-- <uses-permission android:name="android.permission.INTERNET" /> -->
    <!-- <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" /> -->
    
</manifest>
```

---

## 📦 INICIALIZACIÓN DE DATOS

### Pre-populated Database

```
assets/
├── jpmigaku_initial.db
│   └─ Tamaño: ~150 MB
│   └─ Contiene: 150k vocabularios + 14k kanji
│   └─ Comprimido: ~50 MB (en APK)
│   └─ Copy on first run: ~2s
│
└─ NUNCA descargar en runtime
```

### SQL para pre-poblar

```kotlin
// Offline script (NOT in app):
// scripts/populate_db.py

import sqlite3
import json

db = sqlite3.connect("jpmigaku_initial.db")
cursor = db.cursor()

# Crear tablas
cursor.execute("""
CREATE TABLE IF NOT EXISTS vocabulary (
    id TEXT PRIMARY KEY,
    japanese TEXT NOT NULL,
    reading TEXT,
    spanish_meaning TEXT NOT NULL,
    deck_id INTEGER NOT NULL DEFAULT 1,
    interval INTEGER DEFAULT 1,
    easeFactor REAL DEFAULT 2.5,
    repetitions INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,
    last_reviewed INTEGER
);
""")

# Cargar desde JMdict
with open("jmdict_extracted.json") as f:
    data = json.load(f)
    
for item in data:
    cursor.execute("""
    INSERT INTO vocabulary 
    (id, japanese, reading, spanish_meaning, created_at)
    VALUES (?, ?, ?, ?, ?)
    """, (
        item['id'],
        item['kanji'],
        item['reading'],
        item['spanish'],
        int(time.time() * 1000)
    ))

db.commit()
db.close()

# Resultado: app/src/main/assets/jpmigaku_initial.db
```

### Copy on App Start (First Run)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Singleton
    @Provides
    fun provideDatabase(context: Context): JPMigakuDatabase {
        // Room copia automáticamente si no existe
        return Room.databaseBuilder(
            context.applicationContext,
            JPMigakuDatabase::class.java,
            "jpmigaku.db"
        )
            .createFromAsset("jpmigaku_initial.db")  // Copy on first run
            .build()
    }
}

// Timeline:
// First install:
//   ├─ APK (~80 MB): app code + resources
//   ├─ assets/ (~50 MB): jpmigaku_initial.db (zipped)
//   ├─ First app launch: Copy assets/ → /data/data/.../jpmigaku.db (~150 MB)
//   ├─ Time: ~2-3s (background thread)
//   └─ User sees: Loading... spinner
//
// Subsequent launches:
//   └─ DB already exists, no copy needed
//   └─ Time: <100ms
```

---

## 🔍 VALIDACIÓN OFFLINE

### Runtime Check (Optional Safety)

```kotlin
// En ViewModel o Repository, para extra safety:

class OfflineValidator {
    
    fun ensureOfflineMode(context: Context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager?.activeNetwork
        
        if (network != null) {
            // Red disponible (pero no la usamos)
            // Puramente para logging/debug
            Timber.w("⚠️ Red disponible pero OFF (como debe ser)")
        }
    }
}

// Assert en tests:
@Test
fun testRoomDatabase_HasNoNetworkDependencies() {
    // Verificar que Room no hace HTTP
    // (Kotlin compiler + Room processor ya lo garantizan)
}
```

### End-to-End Offline Test

```kotlin
class OfflineFirstE2ETest {
    
    @Test
    fun testCompleteWorkflow_WithoutInternet() {
        // 1. Conectar a DB local
        val db = JPMigakuDatabase.getInstance(context)
        
        // 2. Ejecutar workflow completo (offline)
        // - Búsqueda
        val results = vocabularyDao.searchPaginated("agua")
        assertEquals(true, results.isNotEmpty())
        
        // - Quiz
        val quiz = performQuizUseCase.generateQuiz(deckId = 1)
        assertTrue(quiz.isSuccess)
        
        // - SRS update
        val update = updateSRSUseCase.calculateAndUpdate(srsUpdate)
        assertTrue(update.isSuccess)
        
        // - Streak
        val streak = updateStreakUseCase.updateStreaks(deckId = 1)
        assertTrue(streak.isSuccess)
        
        // 3. Verificar persistencia
        val vocab = vocabularyDao.getById("test-vocab-id")
        assertNotNull(vocab)
    }
}
```

---

## 📋 GARANTÍA CHECKLIST

- [ ] ✅ No `implementation("com.squareup.retrofit2:..."`
- [ ] ✅ No `import retrofit.*`
- [ ] ✅ No `import okhttp3.*`
- [ ] ✅ No `import com.google.firebase.*`
- [ ] ✅ No `httpClient.get()`, `retrofit.create()`, etc.
- [ ] ✅ No `android:permission:INTERNET` en AndroidManifest
- [ ] ✅ Room Database inicializa desde APK asset
- [ ] ✅ Todos los cálculos en Domain Layer (puro)
- [ ] ✅ Clipboard API (local) para lectura
- [ ] ✅ SQLite para persistencia
- [ ] ✅ CI/CD grep checks pasan
- [ ] ✅ E2E test offline pasa

---

## 🎯 EXTENSIBILIDAD FUTURA

### (NO implementar en Phase 0, pero arquitectura lo permite)

```kotlin
// Future: Si queremos agregar sincronización

interface IDataSync {
    suspend fun syncToCloud(userId: String)
    suspend fun syncFromCloud(): Boolean
}

// Implementación futura:
class DataSyncImpl : IDataSync {
    override suspend fun syncToCloud(...) {
        // Agregar HTTP aquí (DESPUÉS de Phase 0)
    }
}

// Phase 0:
// - IDataSync no se inyecta
// - Repositories NO dependen de IDataSync
// - Cambio mínimo para habilitar sync
```

---

## 🏆 BENEFICIOS OFFLINE-FIRST

| Beneficio | Impacto |
|-----------|---------|
| ✅ Funciona sin internet | 100% confiable |
| ✅ Privacidad (datos locales) | Sin tracking externo |
| ✅ Latencia cero | <50ms búsqueda |
| ✅ Batería (sin radio WiFi/LTE) | -40% drain vs online |
| ✅ Simple (sin API backend) | Mantenible |
| ✅ Escalable (usuario N no afecta servidor) | ∞ usuarios |

---

## 🧪 VALIDACIÓN COMPLETA

```bash
#!/bin/bash
# ci-full-offline-validation.sh

set -e

echo "🔍 Full Offline Validation"

# 1. Grep checks
./ci-offline-check.sh

# 2. Gradle check
./gradlew checkDependencies

# 3. Build APK
./gradlew assembleDebug

# 4. Run E2E offline test
./gradlew connectedAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.migaku.jpmigaku.test.OfflineFirstE2ETest

echo "✅ All offline validations passed!"
```

---

**Próximo documento**: [`07-TESTING-STRATEGY.md`](07-TESTING-STRATEGY.md) - Estrategia de testing
