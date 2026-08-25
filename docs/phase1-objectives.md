# Objetivos propuestos - Fase 1

## Propósito

Convertir el prototipo visual de Fase 0 en una primera versión de estudio
utilizable con vocabulario y kanji locales, manteniendo el enfoque offline-first.
La arquitectura base y el pseudocódigo de referencia siguen en `architecture/`.

## Objetivos prioritarios

### 1. Diccionario local

- Integrar una fuente local versionada para vocabulario y kanji (por ejemplo,
  JMdict y KANJIDIC, sujeto a confirmar licencia y formato).
- Diseñar una importación reproducible, validada y cancelable.
- Guardar los datos del diccionario separados de las entradas personales.
- Permitir actualizar la base local sin perder decks, revisiones ni progreso.

### 2. Modelo y práctica de kanji

- Crear entidades, DAOs, repositorios y casos de uso para kanji.
- Soportar significado, lecturas onyomi/kunyomi y nivel cuando exista.
- Añadir modos `kanji -> significado`, `significado -> kanji` y
  `kanji -> lectura`.
- Reutilizar el SRS del vocabulario mediante una abstracción común de revisión.

### 3. Búsqueda y selección

- Buscar vocabulario y kanji localmente por texto japonés, lectura y español.
- Mostrar detalle antes de añadir una entrada a un deck.
- Permitir selección múltiple y añadir resultados a un deck.
- Añadir índices y límites de consulta adecuados para el tamaño del diccionario.

### 4. Flujo de navegación y UX

- Mantener el hub extensible `Buscar / Quiz / Kanji / Vocabulario`.
- Hacer explícita la interacción de las cajas de texto del hub sin convertirlas
  visualmente en botones, o confirmar una alternativa de accesibilidad.
- Separar visual y funcionalmente alta manual de vocabulario y alta de kanji.
- Añadir estados de carga, vacío, error y confirmación en las pantallas principales.

### 5. Progreso y estadísticas

- Implementar streak global y por deck con fechas locales/UTC definidas.
- Registrar sesiones de quiz, aciertos, fallos y tarjetas revisadas.
- Mostrar estadísticas mínimas: progreso diario, repasos pendientes y rendimiento
  por deck.

### 6. Calidad y distribución

- Añadir tests de integración de Room y tests de casos de uso SRS.
- Añadir tests de navegación y de los flujos de búsqueda, importación y quiz.
- Verificar que la aplicación funciona sin red y no contiene llamadas HTTP.
- Generar APK debug reproducible para pruebas en Android 12 o superior.

## Fuera de alcance

- Login, cloud, sincronización y backend.
- IA, OCR avanzado y traducción automática.
- Gamificación compleja o ranking.
- Internacionalización de la UI: se mantiene español en esta fase.
- Caligrafía, reconocimiento de trazos y audio avanzado.

## Decisiones requeridas antes de implementar

1. Confirmar las fuentes de diccionario y sus licencias de distribución.
2. Confirmar si la importación se realiza dentro de la app o durante el build.
3. Definir si una palabra del diccionario y una entrada personal pueden compartir
   identidad o deben permanecer siempre separadas.
4. Definir el comportamiento de los botones/cajas `Buscar` y `Quiz` del hub:
   visualmente son cajas, pero necesitan un mecanismo de activación accesible.
5. Confirmar las reglas exactas de streak, zona horaria y cambio de día.
6. Confirmar si los decks pueden contener vocabulario y kanji conjuntamente.

## Criterio de salida

Fase 1 estará lista cuando una instalación limpia pueda importar los datos
locales, buscar vocabulario y kanji, añadirlos a decks, practicar los tres tipos
de contenido, actualizar SRS y mostrar estadísticas básicas sin conexión.
