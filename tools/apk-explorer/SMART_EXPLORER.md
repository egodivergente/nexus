# APK Explorer 0.4.0 · explorador inteligente

Rama: `codex/apk-explorer-smart-20261027` (parte de `codex/apk-explorer-20261015`, commit `872736b`).
Versión recuperable: etiqueta Git `apk-explorer-0.3.0-baseline` y APK 0.3.0 en `Desktop\apps\APK-Explorer-0.3.0-preview.apk`.
Misma firma de depuración que 0.3.0: se instala encima conservando datos (`adb install -r`).

## Qué fallaba en 0.3.0 (reproducido en S25, Android 17, auditando NEXUS Agent)

| Síntoma | Causa en el código |
|---|---|
| Se quedaba atrapado en una sola sección | Voraz y en profundidad: «no repetir etiqueta» a nivel de sesión, sin modelo de pantallas ni grafo; sin forma de volver a las demás secciones. |
| Capturas casi iguales | Duplicado = comparar con miniatura 64×128 de forma exacta; no distinguía variaciones de contenido de pantallas nuevas. |
| Capturas en momentos raros | Espera fija de 1,4 s tras cada acción (ni transiciones largas ni cargas). |
| Pulsaba acciones peligrosas | `ActionPolicy` no cubría «quitar», «marcar todo», «adjuntar», «responder», «hacer una foto», interruptores, contadores… |
| Informe pobre | `AUDIT.md` genérico, nombres `screen-0001.png`, sin omitidos ni errores, resultados solo en memoria (sin reanudar). |

## Qué hace 0.4.0

- **Identidad de pantalla** (`ScreenSignature`): jerarquía normalizada (dígitos, relojes, UUID fuera), selección de pestañas/filtros y nº de ventanas (diálogos, menús). Más huella perceptual de 256 bits (`PerceptualHash`).
- **Espera adaptativa** (`StabilityTracker`): estructura y píxeles estables, indicadores de carga, animaciones aceptadas si la estructura no cambia, topes de tiempo y avisos.
- **Modelo de exploración** (`ExplorationModel`, sin dependencias de Android): grafo de transiciones observadas, búsqueda de ruta, elección por coste (rango + profundidad + distancia: pestañas y menús primero), retroceso inteligente, reinicio sin borrar datos, detección de bucles y de estancamiento, salida de la app y recuperación.
- **Candidatos** (`Candidate`): clasifica pestañas / navegación / elementos de lista repetidos; omite campos de texto, interruptores, casillas sueltas, capas de fondo, mensajes y sugerencias en pantallas de chat. Todo lo omitido queda en el informe con su motivo.
- **Política ampliada** (`ActionPolicy`): ver `PolicyRegressionTest` con etiquetas reales.
- **Capturas solo con información nueva**, `screens/<sección>/NNN-<título>.png` + `.xml`, escritas a disco en cada paso.
- **Informe**: `REPORT.md` (explorado, omitido, errores, rutas), `index.html` por secciones, `exploration.json`, `agent.json` (schema 2 compatible con `visual_audit.py`) y `route.json`.
- **Reanudar**: `session.json` en `/data/local/tmp/apk-explorer/<paquete>/session` tras cada paso. En la app aparece «Reanudar auditoría»; por shell: `... Engine <pkg> <n> <zip> resume`. Nunca se borra una sesión: la anterior se mueve a `session-<epoch>`.
- **Salvaguarda**: si la app auditada está en la pantalla principal, el motor se niega a ejecutar.

## Compilación y pruebas

En AXELPC (JDK 17, SDK 35):

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --max-workers=2 --no-parallel
```

36 tests JUnit, incluida una simulación completa de una app (pestañas, listas, ciclos, controles peligrosos, reanudación).
`../eval_audit_zip.py <zip> [--sheet hoja.png]` mide un ZIP con una huella independiente del motor (capturas, únicas visuales, duplicadas).

## Límites conocidos

- No hace scroll al explorar (el modo repetición admite pasos `scroll` para llegar a pantallas largas).
- Los controles solo con icono y sin etiqueta se omiten (informado).
- Un chip de filtro (casilla en fila de 3 o más) se pulsa; una casilla/interruptor aislado, no.
- La heurística de política es conservadora pero no puede conocer todas las apps: revisa «Omitido» y usa el modo de repetición para ensayos.
