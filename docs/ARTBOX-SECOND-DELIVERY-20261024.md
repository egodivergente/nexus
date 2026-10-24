# ArtBox: segunda entrega, 2026-10-24

## Continuidad y límites

Rama `codex/artbox-integral-2-20261024`, desde la primera entrega local
`13fa404f7749ca20c342403aeb9ee51682a81944`. Base remota ArtBox documentada:
`d21cfe1bdeaa2bbd7538de876ec1951039c0cbde`, no el main antiguo.
Se reutilizan el editor nativo y las correcciones de catálogo, Atlas y fullscreen.
`gradlew.bat` tenía cambios ajenos al empezar: no se incluyen ni revierten.
Sin publicación, instalación, generación, modificación de claves ni borrado de datos.

## Implementado

- Chat: acciones de pulsación prolongada para ambos autores; copia íntegra y compartir
  usan el texto original. Selección de fragmentos en diálogo desplazable sin convertir
  Markdown, enlaces ni saltos. Acciones semánticas accesibles.
- Accesos del compositor: punto discreto con botón de 48dp, abre/cierra recientes;
  eliminado el activador de arrastre del compositor. Se conserva Atrás para cerrar el panel.
- Navegación: se reutiliza HorizontalPager, independiente del historial. Ahora reserva
  los insets horizontales de gestos del sistema. No se intercepta Atrás para cambiar pestaña.
- Logos: preferencia independiente del catálogo, por familyId normalizado; override por
  modelo, restauración a logo de familia y restauración del logo original. Selector SAF
  con permiso persistente; error de lectura vuelve al logo original. Mantener pulsado el logo.
- Encargos: menú de error con descartar, eliminar tarjeta conservando historial/medios y
  revisar reintento. Revisar reconstruye el mismo encargo y exige confirmación posterior;
  no envía por sí solo. Historial conserva sus acciones de archivar/restaurar y preparar de nuevo.
  La eliminación del historial no se ofrece si existen resultados válidos.
- Restauración: elimina tarjetas descartadas y huérfanas de plan; deduplica usando la
  huella de parámetros, prompt y roles de medios, no solo el vídeo/imagen. Dos instrucciones
  distintas sobre la misma referencia ya no se fusionan. Trabajo activo sin registro local:
  estado incierto, no reenvío automático. Los envíos inciertos no se convierten en fallos reintentables.
- Skill existente `model_router` actualizada e integrada en `recommend_models`: modo experto
  exige schema compatible, no ajusta silenciosamente duración/resolución, valida campos de
  referencia y último frame. Rapidez, sufijos pro/fast y novedad no otorgan calidad por sí solos.
  Se distinguen criterios editoriales de evidencia; no se muestran scores al agente.
  Precio secundario: desempate solo con cotización total, fuente y timestamp de hasta una hora.
  Precios de múltiples referencias quedan desconocidos hasta poder cotizar su configuración exacta.
- Agente: eliminado el añadido lateral de modelos Flow que saltaba el catálogo visible.
  Los límites de prompt se toman del modelo configurado con su schema, no del catálogo anterior.
- Herramientas: excluye generación convencional de audio; incluye edición de imagen sin
  tarea de generación desde texto. Se mantienen categorías especializadas y jerarquía existente.

## Fal: investigación verificable, integración NO terminada

Fuentes oficiales consultadas:
- https://fal.ai/docs/platform-apis/v1/models
- https://fal.ai/docs/platform-apis/v1/models/pricing
- https://fal.ai/docs/platform-apis/v1/models/pricing/estimate

Prueba pública real, sin generación: GET `/v1/models` con
`endpoint_id=fal-ai/flux/dev&expand=openapi-3.0` devuelve modelo activo y OpenAPI.
GET `/v1/models/pricing?endpoint_id=fal-ai/flux/dev` sin clave devuelve HTTP 401.
No hay FAL_KEY/FAL_API_KEY en el entorno de este proceso; no se han extraído credenciales
del dispositivo ni sustituido las de Atlas. La documentación exige autenticación para precios
y contempla descuentos por cuenta. Una tarifa pública por unidad no demuestra equivalencia
ni ahorro para una configuración completa. No se añade un proveedor aparente ni precios
inventados. Fal, selector de proveedores y comparación de prestaciones quedan pendientes.

## Entrega anterior: alcance de la evidencia

Ver `ARTBOX-LOCAL-INPAINT-ATLAS-20261024.md` y commit base. Existe implementación real de
máscara/S Pen, descarga, progreso, cancelación, JNI y guardado; el motor fue cargado en S25
sin instalar APK. NO hay prueba de inferencia con pesos ni aceptación de añadir/cambiar/borrar.
El bloqueo observado fue la reserva conservadora de RAM de la aplicación, no una prueba de
incompatibilidad del hardware. Tampoco se valida una alternativa de PC como si ya funcionara.
Atlas utiliza el servicio oficial; el ejecutable CLI sigue bloqueado por la política de Windows.
Fullscreen y tarjetas conservan correcciones anteriores: la compilación no acredita su apariencia
en S25 ni su comportamiento con teclado, S Pen y gestos reales.

## Validación y pendientes

Comandos finales (AXELPC, un worker, sin instalar):

```powershell
.\gradlew.bat :app:testOfflineQaUnitTest :agent-core:test :app:assembleOfflineQa :app:compileOfflineQaAndroidTestKotlin --max-workers=1 --no-parallel --console=plain
.\tools\Test-InpaintApk.ps1 -ApkPath app/build/outputs/apk/offlineQa/app-offlineQa.apk
git diff --check
```

Regresiones nuevas: restauración sin fusionar prompts/parámetros distintos; descartados y
huérfanos; precedencia/restauración de logos; audio fuera de Herramientas; experto rechaza
duración/resolución no soportada, ignora rapidez y no compara una cotización vencida como vigente.
Pruebas UI nuevas `ChatMessageActionsTest`: menús y selección original de ambos autores.
Solo se compilan: ejecutarlas con Gradle connectedAndroidTest instalaría APK, prohibido en esta tarea.
Pendientes de aceptación física: gestos de bordes y extremos, scroll/selección/compartir,
persistencia real de permisos SAF, layouts estrechos/fullscreen y recuperación Flow tras reinicio.
No se ejecuta una generación de Flow para validar reintentos ni una generación de pago.

## Capturas universales: solo documentado

Fuera del alcance de implementación. Requisito futuro: captura del sistema o S Pen sin
dependencia de Nexus; copia a carpeta Drive autorizada, con exclusión de contenido sensible,
estado de sincronización visible y permisos del usuario. No se habilitan automatismos ni se
suben capturas en esta entrega.

## Resultado final comprobado

- Gradle: BUILD SUCCESSFUL; última ejecución 54 segundos.
- App: 677 tests, cero fallos/errores/omitidos.
- Agent-core: 316 tests contabilizados, cero fallos/errores, 4 omitidos: 312 ejecutados.
- Total: 989 pruebas ejecutadas PASS; 4 omitidas. No confundir con 993 ejecutadas.
- Suite Android/UI completa compilada PASS; cero pruebas UI ejecutadas en dispositivo.
- `Test-InpaintApk.ps1`: seis bibliotecas nativas obligatorias PASS.
- APK `app/build/outputs/apk/offlineQa/app-offlineQa.apk`: 86.617.771 bytes.
- SHA-256 `531EFEA3BF4CB3DAD27463604760AE180C66D9D72E1738F1479D3B83EA0C794B`.
- Log local: `app/build/integral-2-final-validation.log`; informes XML bajo
  `app/build/test-results/testOfflineQaUnitTest` y `agent-core/build/test-results/test`.
- `git diff --check`: PASS. Búsqueda acotada de patrones de claves/tokens en archivos
  modificados: cero coincidencias; no equivale a una auditoría completa con gitleaks.
- S25 sin cambio de instalación: `com.vidadomu.nexus.agent`, vc109,
  1.9.75-agent-preview, lastUpdateTime 2026-10-24 11:34:59.
- Esta APK QA no se ha instalado ni publicado; tampoco se ha hecho push.
