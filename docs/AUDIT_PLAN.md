# Auditoría premium de Nexus Agent — 29/10/2026

## Procedencia y límites
- AXELPC; rama propia `codex/nexus-premium-20261029`, base remota `dead4032` (vc155 / 1.9.119), descendiente de la entrega v137. Ambos dispositivos declaran vc155. Leídos AGENTS.md, CONTINUAR.md y MIGRATION-STATUS.json; los dos últimos contienen historial anterior y sus restricciones antiguas quedan sustituidas por la autorización actual.
- Pixel 8 Pro `38271FDJG00264`; Samsung S25 Ultra SM-S938B `R5CXC3J630Y`. Usar siempre serial ADB explícito. Conservar datos, configuración y certificado. Sin generaciones/LLM de pago, sin cambios de proveedores, contratos o arquitectura.
- La auditoría es un registro vivo. Una calificación 10 subjetiva no sustituye pruebas. No se declarará 60/120 fps, TalkBack o háptica física comprobada sin evidencia.
- Capturas, APK, logcat y datos privados: fuera de Git, en `C:/Users/User/Documents/Codex/nexus-premium-20261029`.

## Plan por impacto y riesgo
| ID | Prioridad / riesgo | Hallazgo y acción | Aceptación / estado inicial |
|---|---|---|---|
| P01 | Alta / bajo | Reconciliar versión instalada, firma, fuente, historial/datos y actividad; rama aislada. | vc155 observada en ambos; fuente remota localizada. Firma y preservación por verificar. |
| P02 | Alta / medio | Auditar en Pixel y S25 arranque, agente, ArtBox, Lanzadera, Biblioteca, Ajustes y submenús, carga/error/vacío, IME y navegación. | Pixel abre chat con errores históricos; S25 CE/almacenamiento no disponible mientras está bloqueado. No forzar autenticación. |
| P03 | Alta / bajo | Consolidar escala visual en componentes compartidos, colores, radios, espacios y controles; corregir usos literales donde ya existe token. | Auditar componentes, ajustes y pantallas reales; sin refactor masivo. |
| P04 | Alta / medio | Revisar tipografía, contraste y escalado de fuente, controles táctiles y semántica; corregir recortes y pérdida de información en compacto/ancho. | Barra inferior reduce explícitamente escala de fuente; acciones compartidas declaran alturas inferiores a 48dp. |
| P05 | Alta / bajo | Háptica centralizada por selección, confirmación, error y gesto; respetar preferencias Android/app, evitar duplicación y efectos pasivos. | Conviven NexusHaptics, LocalHapticFeedback y vibración directa; revisar rutas y consolidar. |
| P06 | Alta / medio | Insets y teclado, estado al volver de ajustes, feedback inmediato, cancelación y deshacer seguro en acciones destructivas. | Revisión de MainActivity, chat, Biblioteca y hojas; no borrar contenido para probar. |
| P07 | Media / medio | Rendimiento: composición/animaciones/carga, movimiento reducido y ciclo de vida; corregir jank de causa concreta. | Tomar gfxinfo y logcat en flujos tocados; no equiparar muestreo con benchmark 120Hz. |
| P08 | Alta / bajo | Compilar cada bloque coherente en AXELPC y verificar pruebas relevantes, diff propio y actualización Pixel; después regresión S25. | Sin suites interminables ni APKs de aplicación paralelos. Reutilizar JNI solo si su integridad y procedencia se comprueban. |
| P09 | Alta / bajo | Segunda auditoría independiente del resultado, resolver hallazgos reales y documentar descartes/bloqueos por ítem. | Pendiente. |
| P10 | Alta / bajo | APK final, hash, certificado, actualización en ambos, versión/arranque; commits atómicos, push y verificación remota; AUDIT_REPORT. | Pendiente. |

## Criterios de decisión
Conservar la identidad visual y los flujos existentes; priorizar mejoras compartidas con beneficio observable. Sin dependencias nuevas. Riesgo irreversible o datos inaccesibles: registrar pendiente humana y continuar. No más de dos intentos iguales; diagnosticar la causa antes de reintentar. No confundir bloqueo de pantalla, fallo del proveedor, compilación y aceptación visual.

## Evidencia y decisiones durante la ejecución
- S25 informa usuario 0 activo y vc155 instalado, pero `run-as` no encuentra CE, `/sdcard` no existe y el proveedor/actividad no se resuelven mientras permanece bloqueado. La conexión ADB por sí sola no prueba acceso de la app.
- Pixel: estado nativo 466 trabajos, 4 proyectos, 2156 recursos; pagos deshabilitados. El chat conserva errores previos. No reenviar mensajes históricos ni cambiar el modelo para auditarlos.
