# JP Migaku

JP Migaku es una aplicación Android orientada al estudio del japonés y
concebida para funcionar sin conexión. Su objetivo es ofrecer una base
extensible para organizar contenidos de estudio, crear tarjetas personales y
repasar mediante un modo de estudio local.

El proyecto se encuentra actualmente en fase experimental (`0.1.0`). La
implementación del repositorio es la fuente de verdad; las APIs, pantallas y
algoritmos pueden cambiar durante el desarrollo.

## Estado y alcance

La aplicación está diseñada para incorporar distintos campos de aprendizaje
de forma progresiva. El modelo no queda limitado a un tipo concreto de
contenido: en el futuro podrá incluir, entre otros, conjugaciones, gramática
y nuevas clases de información lingüística.

La funcionalidad de repaso disponible se organiza alrededor de un modo de
estudio con tarjetas, programación de revisiones y sesiones configurables.
Los detalles de los ejercicios y de los tipos de contenido se mantienen
deliberadamente fuera de esta descripción mientras la aplicación evoluciona.

## Características actuales

- Búsqueda local en los datos lingüísticos incluidos.
- Preferencia por significados en español, con fallback a inglés cuando no
  hay información en español.
- Tarjetas personales y organización local en decks.
- Modo de estudio para repasar tarjetas.
- Programación y priorización de revisiones pendientes.
- Datos lingüísticos integrados para utilizar la aplicación sin conexión.

## Requisitos

- Android Studio con un Android SDK que proporcione API 35.
- Se recomienda JDK 21 con la configuración actual de Gradle y Kotlin.
- Android 12L/API 32 o superior en el dispositivo (`minSdk = 32`).

El proyecto genera bytecode compatible con Java/Kotlin 17. El wrapper de
Gradle está incluido, por lo que no es necesario instalar Gradle por
separado.

## Compilar y probar

En Windows:

```powershell
.\gradlew.bat test
```

En macOS o Linux:

```bash
./gradlew test
```

Android Studio puede crear `local.properties` con la ruta del SDK local. Ese
archivo está ignorado intencionadamente y no debe subirse al repositorio.

## Estructura del proyecto

```text
app/src/main/java/com/jpmigaku/app/
  data/          Persistencia Room, importación de datos y repositorios
  domain/        Modelos de dominio y casos de uso
  presentation/ Interfaz Compose y estado de la aplicación
app/src/main/assets/dictionaries/
                 Datos lingüísticos locales
gradle/          Wrapper de Gradle
```

La aplicación almacena localmente las tarjetas y el estado de las revisiones
mediante Room. Los datos lingüísticos se importan a un catálogo local durante
el primer uso. No se necesita ningún servicio externo para ejecutar la
aplicación.

## Fuentes de datos y atribución

Los archivos de datos incluidos son de terceros y no están cubiertos por la
licencia MIT del código original de JP Migaku:

| Datos | Fuente | Licencia y atribución |
| --- | --- | --- |
| Datos JMdict en español e inglés | [jmdict-simplified](https://github.com/scriptin/jmdict-simplified), versión 3.6.2 | CC BY-SA 4.0; se aplican las obligaciones de JMdict y del Electronic Dictionary Research and Development Group (EDRDG) |
| KANJIDIC2 | [jmdict-simplified](https://github.com/scriptin/jmdict-simplified), versión 3.6.2 | CC BY-SA 4.0; se aplican las obligaciones de KANJIDIC2 y del EDRDG |
| Clasificaciones JLPT | [japanese-language-data](https://github.com/jkindrix/japanese-language-data) | Redistribución CC BY-SA 4.0, con atribución CC BY 4.0 para los datos de Jonathan Waller y las obligaciones de las fuentes correspondientes |

Los metadatos exactos y las versiones de los archivos están documentados en
`app/src/main/assets/dictionaries/README.txt`. Las clasificaciones JLPT son
datos comunitarios y no representan información oficial del examen JLPT.

## Software de terceros

JP Migaku utiliza las siguientes herramientas y bibliotecas de código
abierto. Las licencias de sus respectivos proyectos siguen siendo aplicables
a esos componentes:

| Componente | Uso | Licencia |
| --- | --- | --- |
| [Android SDK y AndroidX](https://developer.android.com/) | Plataforma Android, ciclo de vida, actividad, navegación, Room y APIs de pruebas | Apache License 2.0 |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | Interfaz declarativa | Apache License 2.0 |
| [Material Components for Android](https://github.com/material-components/material-components-android) | Componentes Material para Android | Apache License 2.0 |
| [Kotlin](https://kotlinlang.org/) | Lenguaje de la aplicación | Apache License 2.0 |
| [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Trabajo asíncrono | Apache License 2.0 |
| [Gradle](https://gradle.org/) | Sistema de compilación y wrapper | Apache License 2.0 |
| [Android Gradle Plugin](https://developer.android.com/build) | Integración de compilación Android | Apache License 2.0 |
| [KSP](https://github.com/google/ksp) | Procesamiento de símbolos Kotlin | Apache License 2.0 |
| [Dagger Hilt](https://dagger.dev/hilt/) | Inyección de dependencias | Apache License 2.0 |
| [JUnit 4](https://github.com/junit-team/junit4) | Pruebas unitarias | Eclipse Public License 1.0 |
| [Espresso](https://developer.android.com/training/testing/espresso) | Pruebas de interfaz Android | Apache License 2.0 |

Las versiones se mantienen en `build.gradle.kts` y
`app/build.gradle.kts`. Los avisos de copyright y las condiciones de las
licencias de terceros pertenecen a sus respectivos proyectos y
distribuciones.

## Descargo de responsabilidad

JP Migaku es un proyecto educativo independiente. No está afiliado,
respaldado ni patrocinado por el Electronic Dictionary Research and
Development Group, JMdict, KANJIDIC2, los administradores del JLPT, Google,
Android, JetBrains ni ningún otro proyecto de terceros mencionado.

Los datos lingüísticos incluidos pueden contener errores u omisiones. Las
clasificaciones JLPT no constituyen una guía oficial del examen. Verifica la
información lingüística importante con fuentes autorizadas. El software y
los datos incluidos se proporcionan sin garantía y están sujetos a sus
licencias aplicables.

## Licencia

El código original y los assets originales de JP Migaku se distribuyen bajo
la [licencia MIT](LICENSE), excepto los datos y el software de terceros
identificados anteriormente, que conservan sus propias licencias.
