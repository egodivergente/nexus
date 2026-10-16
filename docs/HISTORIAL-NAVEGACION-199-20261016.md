# Historial: navegación visible frente a rutas del código (2026-10-16)

## Discrepancia

La auditoría indicaba que **Historial no existe** en la interfaz, mientras el código
define `GenerationsScreen`, la ruta `Screen.Generations` y la incluye en
`Screen.bottomNavItems`. Ambas cosas eran ciertas, en compilaciones distintas.

| Compilación | `bottomNavItems` | ¿Historial alcanzable? |
| --- | --- | --- |
| `main` (1.9.6-agent / vc40) y anteriores, incluida 1.9.5 vc39 | Agente, Lienzo, Modelos, Biblioteca (4) | **No.** La ruta `generations` existía en el `NavHost`, pero ningún botón ni aviso navegaba a ella. El test `OutputExportSpecTest` de `main` exige precisamente 4 destinos y Historial fuera de la barra. |
| 1.9.7-agent / vc41 (antes de `ec9cf9c`) | 4 destinos | No, igual que `main`. |
| Desde `ec9cf9c` (1.9.8-agent / vc42) | Agente, Lienzo, Modelos, **Historial**, Biblioteca (5) | Sí, como pestaña. |

Por tanto, una interfaz con cuatro destinos corresponde a una APK compilada antes de
`ec9cf9c`. La auditoría describe esas compilaciones, no la fuente de esta rama.

## Comprobación en dispositivo

- **S25 Ultra (SM-S938B)**, por ADB (Wi-Fi y TLS, el mismo equipo): `com.vidadomu.nexus.agent`
  `1.9.8-agent-preview`, vc42, actualizada el 2026-10-16 a las 04:08:53. La APK de
  `app/build/outputs` es de las 04:08:40, posterior a `e9bf4ba` (04:03), así que incluye
  la pestaña. **No se ha podido ver la barra en pantalla**: el teléfono estaba en
  reposo con la pantalla de bloqueo activa, y Nexus no la desbloquea.
- **Pixel 8 Pro**: no estaba conectado por ADB. Según `CONTINUAR.md` tenía 1.9.5 vc39
  (cuatro destinos, sin Historial).

### 1.9.9 vc43 instalada en el S25 (2026-10-16, 05:35)

- **Firma.** La primera APK compilada en AXELPC se rechazó con
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: las APK del proyecto (1.9.6, 1.9.7, 1.9.8) llevan
  el certificado de depuración `8b75db0f…cd7127`, que es `~/.android/debug.keystore` del
  servidor ASUS. El keystore de AXELPC (`287a0c42…`) es otro. La 1.9.9 se firmó con la
  clave del ASUS y se instaló con `adb install -r`, conservando los datos
  (`firstInstallTime` sigue en 2026-10-02). El keystore no está en Git.
- **Cierre al abrir el chat (corregido).** La primera 1.9.9 se cerraba al pintar un aviso
  (`PatternSyntaxException` en `HistoryLinks.<clinit>`): el flag de regex `(?U)` funciona
  en la JVM pero no en el motor de Android (ICU). Se quitó. La APK corregida
  (SHA-256 `ddf0120cdc896a3bd15b9df50b50e63cc56e3a2a1e94253b8e2281d51dfedfc3`) arranca
  sin errores en el registro de fallos.
- **Barra visible.** La jerarquía de la pantalla (uiautomator) y una captura muestran
  cinco destinos: Agente, Lienzo, Modelos, **Historial** `[864,2867][1152,3049]` y Biblio.

## Avisos que mencionan Historial

Con la barra inferior, Historial está a un toque en todas las pantallas salvo dos:
el visor de resultados (`output/*`, que oculta la barra) y el chat con el teclado abierto.
En 1.9.9 los avisos de esos sitios llevan su propio acceso directo **«Abrir Historial»**,
que abre la misma pestaña de la barra (no otra pantalla ni Biblioteca):

- Chat del agente: avisos del sistema y respuestas del agente que remiten a Historial
  («Lo verás en Historial», «Compruébalo en Historial», «disponible en Historial»…).
  El detector (`HistoryLinks`) no confunde «El historial se conserva» (conversación)
  ni «Indexar historial» (índice del agente).
- Tarjeta de encargo: errores que remiten a Historial y el aviso «Envío sin confirmar.
  Revisa Historial…», que antes solo ofrecía «Ver en Nexus» (visor del trabajo) y solo
  si había id de trabajo.
- Visor de resultados: «La tarea sigue guardada en Historial» + «Abrir Historial».

Lienzo, comparaciones, Flow y errores de red se muestran en pantallas con la barra
visible, así que Historial queda a un toque. «Historial de Flow» se refiere a la web de
Google Flow, no al destino de Nexus, y no se ha cambiado.

Historial lista todos los trabajos no archivados, también fallidos e inciertos, así que
lo que prometen los avisos es cierto. Los archivados siguen en su filtro.

## Alcance

Cambian la navegación y los textos. No cambian generación, proveedores, cobros,
colas, persistencia ni claves.
