# 00 - OVERVIEW: Arquitectura JP Migaku Phase 0

**Fecha**: 2026-07-08  
**Versión**: 1.0  
**Status**: Phase 0 - Offline-first

---

## 📊 VISIÓN GENERAL

JP Migaku es una aplicación Android offline-first para estudio de japonés usando repetición espaciada (SRS).

**Objetivo**: Capturar vocabulario/kanji rápidamente (1-2s) y practicarlo mediante quizzes.

**Principios**:
1. **Velocidad de captura** - Guardar en 1-2 segundos
2. **Offline-first** - 100% local, sin internet
3. **Simplicidad extrema** - Sin IA, cloud, NLP avanzado
4. **Repetición espaciada** - SM-2 algorithm simplificado
5. **Robustez por defecto** - Validación temprana, transacciones atómicas y privacidad mínima

**Referencia visual de UX**: [UX guide.png](../UX%20guide.png)  
**Mitigaciones de riesgos**: [08-RISK-MITIGATION.md](08-RISK-MITIGATION.md)

---

## 🏗️ ARQUITECTURA DE CAPAS

### Clean Architecture (3 capas)

```
┌────────────────────────────────────────────────┐
│  PRESENTATION LAYER (Jetpack Compose)          │
│  ┌─────────────────────────────────────────┐   │
│  │ UI Screens (Home, Quiz, Search, Deck)   │   │
│  │ ViewModels (State Management)           │   │
│  │ Navigation (Compose Router)             │   │
│  │ State: StateFlow<UiState>               │   │
│  └─────────────────────────────────────────┘   │
└────────────────────────────────────────────────┘
                      ↓ (Depends on)
┌────────────────────────────────────────────────┐
│  DOMAIN LAYER (Business Logic)                 │
│  ┌─────────────────────────────────────────┐   │
│  │ Use Cases (PerformQuiz, Search, SRS)    │   │
│  │ Models (QuizQuestion, SRSUpdate)        │   │
│  │ Algorithms (SM-2, Streak Calculation)   │   │
│  │ Exceptions (DomainException)            │   │
│  └─────────────────────────────────────────┘   │
└────────────────────────────────────────────────┘
                      ↓ (Depends on)
┌────────────────────────────────────────────────┐
│  DATA LAYER (Persistence)                      │
│  ┌─────────────────────────────────────────┐   │
│  │ Repositories (VocabRepo, KanjiRepo)     │   │
│  │ DAOs (Room Data Access Objects)         │   │
│  │ Entities (Room @Entity classes)         │   │
│  │ Room Database (SQLite local)            │   │
│  └─────────────────────────────────────────┘   │
└────────────────────────────────────────────────┘
```

---

## 🎯 STACK TECNOLÓGICO CONFIRMADO

| Componente | Librería | Versión | Propósito |
|-----------|----------|---------|----------|
| **UI Framework** | Jetpack Compose | 1.6.8 | Interfaz moderna, reactive |
| **Database** | Room | 2.6.1 | SQLite wrapper, type-safe queries |
| **State Mgmt** | StateFlow | kotlin-coroutines | Reactive state, Compose integration |
| **ViewModel** | Jetpack | 2.8.4 | MVVM state holder |
| **Async** | Coroutines | 1.8.0 | Async/await programming |
| **DI** | Hilt | 2.50 | Dependency injection |
| **Navigation** | Compose Navigation | 2.8.0 | Screen routing |
| **Logging** | Timber | 5.0.1 | Structured logging |
| **JSON** | Gson | 2.10.1 | Parsing datasets |
| **Language** | Kotlin | 1.9.24 | Main implementation |
| **Min SDK** | Android 12 | API 32 | Clipboard UX optimization |
| **Target SDK** | Android 15 | API 35 | Latest optimizations |

---

## 📱 ANDROID CONFIGURATION

```
minSdkVersion = 32         (Android 12 - December 2021)
targetSdkVersion = 35      (Android 15 - Q4 2024)
compileSdkVersion = 35     (Build against latest)

Kotlin JVM Target = 17
Gradle Version = 8.0+
```

---

## 🗂️ ESTRUCTURA DE CARPETAS

```
app/src/main/
├── java/com/migaku/jpmigaku/
│   ├── data/
│   │   ├── dao/                    (Room DAOs)
│   │   │   ├── VocabularyDao.kt
│   │   │   ├── KanjiDao.kt
│   │   │   ├── DeckDao.kt
│   │   │   └── SRSHistoryDao.kt
│   │   ├── database/               (Room entities + database)
│   │   │   ├── entity/
│   │   │   │   ├── VocabularyEntity.kt
│   │   │   │   ├── KanjiEntity.kt
│   │   │   │   ├── DeckEntity.kt
│   │   │   │   ├── SRSHistoryEntity.kt
│   │   │   │   ├── GlobalStreakEntity.kt
│   │   │   │   └── DeckStreakEntity.kt
│   │   │   └── JPMigakuDatabase.kt
│   │   └── repository/             (Repository implementations)
│   │       ├── VocabularyRepository.kt
│   │       ├── KanjiRepository.kt
│   │       ├── DeckRepository.kt
│   │       ├── SRSRepository.kt
│   │       └── ClipboardRepository.kt
│   ├── domain/
│   │   ├── model/                  (Domain models)
│   │   │   ├── QuizQuestion.kt
│   │   │   ├── SRSUpdate.kt
│   │   │   ├── StreakUpdate.kt
│   │   │   └── ...
│   │   └── usecase/                (Use cases)
│   │       ├── PerformQuizUseCase.kt
│   │       ├── UpdateSRSUseCase.kt
│   │       ├── SearchVocabularyUseCase.kt
│   │       ├── DetectClipboardUseCase.kt
│   │       └── UpdateStreakUseCase.kt
│   ├── presentation/
│   │   ├── viewmodel/              (ViewModels)
│   │   │   ├── HomeViewModel.kt
│   │   │   ├── QuizViewModel.kt
│   │   │   ├── SearchViewModel.kt
│   │   │   ├── DeckViewModel.kt
│   │   │   └── ClipboardViewModel.kt
│   │   ├── screen/                 (Compose screens)
│   │   │   ├── HomeScreen.kt
│   │   │   ├── QuizScreen.kt
│   │   │   ├── SearchScreen.kt
│   │   │   ├── DeckManagementScreen.kt
│   │   │   └── ClipboardDialogScreen.kt
│   │   ├── component/              (Reusable composables)
│   │   │   ├── QuizCard.kt
│   │   │   ├── VocabularyListItem.kt
│   │   │   └── ...
│   │   └── navigation/
│   │       └── NavGraph.kt
│   ├── di/
│   │   ├── DatabaseModule.kt       (Hilt DB injection)
│   │   ├── RepositoryModule.kt     (Repository injection)
│   │   └── UseCaseModule.kt        (UseCase injection)
│   ├── MainActivity.kt
│   └── JPMigakuApp.kt              (@HiltAndroidApp)
│
├── res/
│   ├── values/
│   │   ├── strings.xml             (SPANISH - Phase 0)
│   │   ├── colors.xml
│   │   ├── dimens.xml
│   │   └── themes.xml
│   ├── drawable/
│   │   └── ic_launcher.xml         (Icono: jp_migaku.png)
│   └── raw/
│       └── jpmigaku_initial.db     (Embedded database)
│
└── AndroidManifest.xml

test/
├── java/com/migaku/jpmigaku/
│   ├── data/
│   │   └── repository/             (Repository tests)
│   ├── domain/
│   │   ├── usecase/                (UseCase unit tests)
│   │   │   ├── UpdateSRSUseCaseTest.kt
│   │   │   ├── UpdateStreakUseCaseTest.kt
│   │   │   └── ...
│   │   └── model/                  (Model tests)
│   └── presentation/
│       └── viewmodel/              (ViewModel tests)

androidTest/
├── java/com/migaku/jpmigaku/
│   ├── integration/                (DB + Repository integration)
│   ├── ui/                         (Compose UI tests)
│   └── e2e/                        (End-to-end workflows)
```

---

## 🎨 PATRONES Y PRINCIPIOS

### Patrones Utilizados

1. **Clean Architecture** - 3 capas independientes
2. **Repository Pattern** - Abstracción de datos
3. **MVVM** - ViewModel con StateFlow
4. **Use Case Pattern** - Lógica de negocio encapsulada
5. **Dependency Injection** - Hilt para inyección de dependencias

### Principios SOLID

- **S** - Single Responsibility: Cada clase tiene UN propósito
- **O** - Open/Closed: Abierto para extensión, cerrado para modificación
- **L** - Liskov Substitution: Substitución de tipos sin quebrar
- **I** - Interface Segregation: Interfaces específicas, no fat interfaces
- **D** - Dependency Inversion: Dependencias en abstracciones

---

## 🔒 GARANTÍAS OFFLINE-FIRST

```
✅ CERO llamadas HTTP
   └─ No hay Retrofit, OkHttp, Firebase, API clients

✅ Toda persistencia = SQLite local
   └─ Room DAOs no hacen HTTP
   └─ Datos iniciales en APK asset

✅ Búsqueda = SQL local
   └─ No hay API REST
   └─ Índices LIKE precompilados

✅ Quizzes = Algoritmo local
   └─ SM-2 en ViewModel/UseCase
   └─ Cálculos en device

✅ Clipboard = API Android local
   └─ ClipboardManager (no internet)
   └─ Detección de japonés en memoria

✅ GARANTÍA VERIFICABLE
   └─ Grep en CI/CD: NO http://, NO firebase, NO retrofit
```

---

## 📊 FLUJOS CRÍTICOS

### 1. Captura de Vocabulario desde Clipboard

```
Usuario copia texto japonés
    ↓
ClipboardDetector.monitorClipboard()
    ↓
isJapanese(text) = true
    ↓
Snackbar: "Vocabulario detectado"
    ↓
Usuario tap "Agregar"
    ↓
Dialog: [Japanese] [Reading] [Spanish] [Deck selector]
    ↓
Usuario edita + confirma
    ↓
VocabularyRepository.addVocabulary()
    ↓
Guardado en SRS automáticamente
    ↓
Toast: "✓ Guardado en [Deck]"
```

### 2. Quiz Workflow

```
Usuario inicia quiz
    ↓
PerformQuizUseCase.generateQuiz(deckId)
    ↓
Carga N preguntas vencidas (SRS)
    ↓
Genera opciones (1 correcta + 3 distractores)
    ↓
QuizScreen muestra pregunta
    ↓
Usuario selecciona opción
    ↓
Valida respuesta
    ↓
UpdateSRSUseCase.calculateSM2()
    ↓
SRSRepository.recordReview()
    ↓
Actualiza interval + ease + last_reviewed
    ↓
Siguiente pregunta
    ↓
Quiz terminado → Session stats
```

### 3. Búsqueda Local

```
Usuario escribe query
    ↓
SearchViewModel.updateQuery()
    ↓
Debounce 300ms
    ↓
SearchVocabularyUseCase.search()
    ↓
VocabularyRepository.searchVocabulary()
    ↓
Room DAO executa SQL LIKE indexado
    ↓
SELECT * FROM vocabulary 
WHERE japanese LIKE '%query%' 
   OR spanish_meaning LIKE '%query%'
LIMIT 50 OFFSET 0
    ↓
Resultados retornados
    ↓
UI muestra lista paginada
```

---

## ⚡ PERFORMANCE TARGETS

| Métrica | Target | Implementación |
|---------|--------|-----------------|
| Búsqueda (p95) | <50 ms | Índices LIKE + paginación |
| Quiz/pregunta | <100 ms | Lazy-load 10-20 |
| Memory pico | <80 MB | No cachear todo |
| Startup | <3 s | Defer DB init |
| Clipboard detection | <50 ms | API 32 optimized |

---

## 📋 COMPONENTES CLAVE POR CAPA

### Data Layer
- `VocabularyEntity` - Room @Entity
- `VocabularyDao` - @Dao con @Query
- `VocabularyRepository` - Interface + implementation
- `JPMigakuDatabase` - @Database

### Domain Layer
- `UpdateSRSUseCase` - SM-2 algorithm
- `PerformQuizUseCase` - Quiz generation
- `SearchVocabularyUseCase` - Search logic
- `UpdateStreakUseCase` - Streak calculation

### Presentation Layer
- `HomeViewModel` - Dashboard state
- `QuizViewModel` - Quiz session state
- `SearchViewModel` - Search + results
- `HomeScreen` - Compose UI

---

## 🚀 SIGUIENTES DOCUMENTOS

1. **[`01-DATA-LAYER.md`](01-DATA-LAYER.md)** - Room entities, DAOs, repositories con pseudocódigo
2. **[`02-DOMAIN-LAYER.md`](02-DOMAIN-LAYER.md)** - Use cases, algoritmos (SM-2, streak) con pseudocódigo
3. **[`03-PRESENTATION-LAYER.md`](03-PRESENTATION-LAYER.md)** - ViewModels, screens, navigation con pseudocódigo
4. **[`04-WORKFLOWS.md`](04-WORKFLOWS.md)** - Flujos end-to-end detallados
5. **[`05-PERFORMANCE.md`](05-PERFORMANCE.md)** - Optimizaciones y benchmarks
6. **[`06-OFFLINE-FIRST.md`](06-OFFLINE-FIRST.md)** - Garantías offline
7. **[`07-TESTING-STRATEGY.md`](07-TESTING-STRATEGY.md)** - Testing pyramid

---

**Próximo**: Abre [`01-DATA-LAYER.md`](01-DATA-LAYER.md) para ver implementación Data Layer con pseudocódigo.
