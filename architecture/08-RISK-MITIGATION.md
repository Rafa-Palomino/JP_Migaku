# 08 - RISK MITIGATION: Mitigaciones para Phase 0

**Fecha**: 2026-07-08  
**Objetivo**: Reducir bugs, mejorar robustez y cerrar brechas de seguridad mínimas para Phase 0.

---

## 🎯 Plan de mitigación

### 1. Robustez de datos y schema
- Implementar `TypeConverter` para `List<String>` en `KanjiEntity`.
- Añadir foreign keys explícitas a `DeckStreakEntity` y `SRSHistoryEntity`.
- Preparar una migración de Room (`version = 2`) para cambios futuros.
- Validar entrada antes de persistir en repositorio.

### 2. Lógica de negocio determinista
- Inyectar un `TimeProvider` para evitar depender de `System.currentTimeMillis()` directo.
- Normalizar fechas de streak a UTC para evitar errores de zona horaria.
- Hacer el SM-2 y el cálculo de streak deterministas y testeables.

### 3. UI y workflows resistentes
- Evitar reintentos duplicados al terminar un quiz.
- Bloquear múltiples `finishQuiz()` simultáneos con un flag.
- Evitar monitoreo de clipboard no deseado: sólo mostrar aviso cuando el contenido parece japonés y no parece texto sensible.
- Validar texto de entrada antes de guardar vocabulario.

### 4. Privacidad mínima para Phase 0
- No almacenar contenido de clipboard salvo el texto que el usuario decida guardar.
- No persistir texto completo de clipboard sin confirmación explícita.
- Limitar longitud de contenido para evitar entradas excesivas o abusivas.

---

## 🧩 Cambios aplicados en pseudocódigo

| Área | Cambio implementado |
|---|---|
| Data Layer | `TypeConverter` + `@TypeConverters` + foreign keys + migration stub |
| Domain | `TimeProvider` + `ValidateVocabularyInputUseCase` + streak UTC-safe + distractor uniqueness |
| Presentation | `QuizViewModel` con guard de doble finish + clipboard privacy guard |
| Workflows | Validación previa + transacción atómica para SRS + streak |
| UX | Referencia visual: `../UX guide.png` |

---

## 🧪 Validación recomendada

- Unit tests para `UpdateSRSUseCase` con `FakeTimeProvider`.
- Unit tests para `UpdateStreakUseCase` en UTC y en cambio de día.
- Tests para `ValidateVocabularyInputUseCase` (vacío, largo, invalid chars).
- Tests de `QuizViewModel` para evitar doble finalización.

---

## 🔗 Referencias

- [00-OVERVIEW.md](00-OVERVIEW.md)
- [01-DATA-LAYER.md](01-DATA-LAYER.md)
- [02-DOMAIN-LAYER.md](02-DOMAIN-LAYER.md)
- [03-PRESENTATION-LAYER.md](03-PRESENTATION-LAYER.md)
- [04-WORKFLOWS.md](04-WORKFLOWS.md)
- [UX guide.png](../UX%20guide.png)
