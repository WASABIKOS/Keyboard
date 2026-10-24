# Wasabi Keyboard

Teclado Android (IME) con distribución QWERTY español, dictado por voz y colores adaptables.

- **Dictado por voz**: botón 🎤 junto a la barra espaciadora (usa `SpeechRecognizer` del sistema, con resultados parciales en vivo).
- **Color adaptable**: sigue modo claro/oscuro; en Android 12+ usa los colores dinámicos del fondo de pantalla (Material You).
- Pulsación larga: números en la fila superior, acentos (á é í ó ú ü ñ ç), puntuación extra.
- Shift: un toque = una mayúscula, doble toque = bloqueo. Mayúscula automática al inicio de frase.
- Capas `?123` y `=\<`, globo para cambiar de teclado, borrado con repetición.

## Obtener el APK
GitHub Actions compila en cada push: descarga `WasabiKeyboard.apk` desde **Releases** o los artefactos del workflow *Build APK*.

Local: `gradle :app:assembleRelease` (requiere Android SDK, JDK 17).

## Instalación
1. Instalar el APK y abrir **Wasabi Keyboard**.
2. Activar teclado → Seleccionar teclado → Permitir micrófono.
