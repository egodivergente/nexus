# Pantalla virtual en el móvil

El modo virtual ejecuta la APK instalada en una pantalla secundaria creada por
scrcpy. El agente recibe PNG, XML, galería y `agent.json`; los toques se envían
con un ID de pantalla explícito. La pantalla física queda disponible para otras apps.
Ambas pantallas comparten la misma aplicación y sus datos.

```powershell
python tools/apk-explorer/explorer.py --inspect com.empresa.app --virtual --json
```

También admite `--inspect C:\ruta\app.apk`, `--project archivo.apk --project-run`
y `--virtual-size 1080x2400/420`. En la ventana, marca **Pantalla virtual**.
El ajuste de la ventana se recuerda. Para agentes, `--virtual` debe ser explícito.

## Requisitos y funcionamiento

- ADB local autorizado, scrcpy con `--new-display` y `--display-ime-policy=local`,
  javac, SDK Android API 34+ y build-tools (d8). Probado con scrcpy 4.1 y S25 Ultra
  Android 17 (API 37). El PC coordina; la app y su renderizado corren en el móvil.
  Todavía no admite `--ssh` ni funcionamiento autónomo sin PC.
- Un lector Java se compila sin Gradle, se ejecuta por ADB como shell y se elimina
  al cerrar. No instala una APK auxiliar ni modifica ajustes globales. UiAutomation
  conserva los servicios de accesibilidad y exporta solo el display seleccionado.
  Su constructor interno puede cambiar entre versiones: ante fallo cancela la sesión,
  sin recurrir a la pantalla física.
- Navegación continua, sin force-stop ni reinicios para reproducir rutas. Termina
  al agotar candidatos, profundidad o límites; cobertura parcial.
- Si la app objetivo está enfocada en la pantalla física, cancela. Usa otra app
  mientras se explora. La comprobación entre operaciones no elimina carreras si
  abres la misma app durante una acción.
- Destruye la pantalla virtual y sus actividades al terminar; no las traslada al
  móvil ni borra datos. Los cambios de la aplicación pueden persistir.
- Teclado configurado como local; el recorrido omite campos de texto. **Escritura
  simultánea no validada**. Android puede alternar el foco entre displays; no se
  promete aislamiento absoluto del teclado, audio, servicios o recursos compartidos.
- No despierta ni desbloquea el móvil. Funcionamiento bajo bloqueo no validado.

## Verificación del 15 de octubre de 2026

NEXUS Agent 1.9.5 build 39: cinco capturas (inicio, agente, lienzo, modelos y
familia), cinco acciones, cero reinicios. La pantalla física pasó a otra aplicación
durante el recorrido. El display 11 se eliminó al terminar. Evidencia privada local:
`sessions/nexus-virtual-20261015-02`. Capturas y datos privados no se publican.

Los informes incluyen `isolation`: display, política IME, estado observado de la
pantalla principal y cierre confirmado cuando es posible. Suite: 60 pruebas PASS.

Segunda app comprobada: ALEXIA (`com.axel.library`, versión
`1.1.66-filtered-import`, código 68), en el mismo S25. Siete interfaces distintas y
diez acciones: biblioteca de proyectos, diálogo de carpeta, contenido de proyecto,
detalle de imagen, diálogo de persona, diálogo de movimiento y visor ampliado.
Los tres diálogos se cancelaron; no se confirmó creación, edición ni movimiento.
Estado final `finished_partial`, sin errores. Display 13 eliminado y mismo launcher
en la pantalla principal antes y después, con foco recuperado al cerrar.
Durante la sesión el foco principal fue temporalmente nulo: esta prueba demuestra
separación visual y navegación, no escritura simultánea. Evidencia privada local:
`sessions/alexia-virtual-20261015-01`.

Referencias:
- [scrcpy virtual display](https://github.com/Genymobile/scrcpy/blob/master/doc/virtual-display.md)
- [Android UiAutomation](https://developer.android.com/reference/android/app/UiAutomation)
- [AOSP UiAutomation](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/app/UiAutomation.java)
