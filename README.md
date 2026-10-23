# Jarvis — asistente personal por voz para Android

App de Android que escucha su voz, le pregunta a Claude (la IA de Anthropic) y responde en voz alta en español.

## Estado del proyecto

| Fase | Qué incluye | Estado |
|---|---|---|
| 1 | Botón → usted habla → Claude responde → Jarvis habla. Ajustes con clave cifrada y modelo. Memoria corta. | ✅ Lista para probar |
| 2 | Herramientas: WhatsApp, llamadas, SMS, alarmas, recordatorios, abrir apps | Pendiente |
| 3 | Decir "Jarvis" para activarlo, botón en la pantalla de inicio, atajo en el panel de notificaciones | Pendiente |
| 4 | Imágenes y videos con APIMart, extras | Pendiente |

---

## Paso 1 — Crear la clave de la API de Claude

La app necesita una "clave de API": es como una contraseña que le permite a Jarvis usar Claude y que se cobra de su saldo.

1. En el navegador del celular abra **https://console.anthropic.com** y cree una cuenta (con su correo o con Google).
2. Recargue saldo: menú **Settings → Billing** (Ajustes → Facturación) → **Buy credits**. Con **5 dólares** alcanza para muchísimo: con el modelo Haiku cada pregunta cuesta una fracción de centavo.
3. Cree la clave: menú **Settings → API keys** → **Create key**. Póngale de nombre `Jarvis`.
4. Copie la clave (empieza por `sk-ant-…`). **Solo se muestra una vez.** Guárdela en un lugar seguro (por ejemplo, en una nota privada) hasta que la pegue en la app.

> ⚠️ Nunca comparta esa clave ni la pegue en GitHub ni en un chat. Si cree que alguien la vio, bórrela en la consola y cree otra.

## Paso 2 — Descargar e instalar la app

Cada vez que hay un cambio en el código, GitHub compila la app solo (tarda unos 5 minutos) y la publica.

1. En el navegador del celular entre a **github.com** e inicie sesión (el repositorio es privado, así que debe estar con su cuenta).
2. Abra su repositorio → toque **Releases** (o vaya a `https://github.com/television6037-oss/Jarvis2/releases`).
3. En la versión más nueva (la de arriba, "Latest"), toque el archivo **Jarvis-0.1.X.apk** para descargarlo.
4. Cuando termine, toque la notificación de descarga (o búsquelo en la app **Archivos → Descargas**).
5. La primera vez Android le dirá que no permite instalar apps de este origen:
   - Toque **Configuración** en el aviso.
   - Active **Permitir de esta fuente** (para Chrome, o la app desde la que abrió el archivo).
   - Vuelva atrás y toque **Instalar**.
6. Si aparece Google Play Protect diciendo "app no reconocida", toque **Más detalles → Instalar de todas formas**. Es normal: la app es suya y no está en la Play Store.

Para **actualizar** a una versión nueva, repita los pasos 2 a 4: se instala encima, sin perder la clave guardada.

## Paso 3 — Primer uso

1. Abra **Jarvis**.
2. Toque **Ajustes**, pegue su clave `sk-ant-…`, elija el modelo y toque **Guardar**.
   - **Haiku 5.5**: rápido y muy barato. Recomendado.
   - **Sonnet 5.5**: más inteligente, unas 20 veces más caro.
3. Toque el botón azul del micrófono. La primera vez le pedirá permiso para usar el micrófono: toque **Permitir**.
4. Hable, por ejemplo: *"¿Qué día es hoy?"* o *"Cuéntame un dato curioso del espacio"*.
5. Jarvis responderá en voz alta. Puede seguir la conversación: *"¿y por qué pasa eso?"*.

Botones útiles:
- Tocar el micrófono mientras Jarvis piensa o habla lo **interrumpe**.
- **Nueva conversación** hace que olvide lo que hablaron. También olvida solo después de 10 minutos sin hablar.

## Si algo no funciona

| Problema | Solución |
|---|---|
| Jarvis no habla, o habla en inglés | Ajustes del celular → busque **Texto a voz** → motor de Google → instale el idioma **Español**. |
| "Este celular no tiene reconocimiento de voz" | Instale o actualice la app **Google** desde la Play Store. |
| "La clave de la API no es válida" | Vuelva a copiar la clave en Ajustes. Revise que no tenga espacios. |
| "No queda saldo" | Recargue en console.anthropic.com → Billing. |
| No aparece ninguna versión en Releases | En el repositorio abra la pestaña **Actions** y mire si la compilación terminó en verde ✅. |

---

## Para desarrolladores

- Kotlin, Android nativo, `minSdk 26`. Claude se llama con el SDK oficial `com.anthropic:anthropic-java`.
- `brain/ClaudeBrain.kt`: bucle de *tool use* con memoria corta (solo se agregan mensajes, nunca se recortan; se reinicia al estar inactivo).
- `brain/JarvisTool.kt`: interfaz de herramientas. Para añadir una, implemente `JarvisTool` y regístrela en `tools/Tools.kt`. Las acciones delicadas deben llamar a `ui.confirm(...)` antes de ejecutarse.
- `settings/SecureSettings.kt`: la clave de la API se cifra con AES-GCM usando una llave del Android Keystore.
- `.github/workflows/build-apk.yml`: compila `assembleDebug` y publica el APK en Releases.
- `keystore/jarvis-debug.keystore`: llave de firma de depuración fija, para que cada APK nuevo se instale como actualización. Es solo para uso personal; no la use para publicar en la Play Store.
