# Nexus 1.6.0 · Comparar modelos

Verificación del 2 de octubre de 2026. Alcance: comparativas de 2 a 6 modelos de imagen; no se han ejecutado generaciones reales.

## Cambios entregados

- Lienzo: «Comparar con otros modelos», búsqueda y selección persistente de 2 a 6 modelos del catálogo configurado.
- Preparación compartida del prompt y las referencias, adaptación de claves y relaciones de aspecto, y explicación de opciones no compartidas. Los archivos incompatibles o sobrantes bloquean el modelo.
- Coste individual y total previo. Una cotización ausente, caducada o que solo describe una tarifa base impide el envío. Los créditos y el dinero se mantienen separados.
- Un envío por modelo, protección frente a doble pulsación, detención de pendientes y ausencia de reintentos automáticos de generación.
- Historial: agrupación de comparativas y cuadrícula con resultado y estado por modelo. La agrupación es local; sus metadatos no se transmiten al proveedor.
- Recuperación del seguimiento de trabajos aceptados y señalización de envíos inciertos sin repetirlos.
- CTA fijo: corregido el recorte bajo la navegación del sistema. Barras de las ventanas de comparación coherentes con la paleta elegida en Nexus.

## Evidencias

| Comprobación | Resultado |
| --- | --- |
| Compilación en ASUSVIDAL, una tarea y un worker, sin paralelismo | PASS: debug y offlineQa |
| Inicio de la variante normal final en emulador API 35 | PASS: actividad iniciada; sin errores AndroidRuntime capturados de su proceso |
| Pruebas JVM | PASS: 189; 0 fallos, 0 errores, 0 omitidas |
| Regresión de comparación | 13 pruebas: contratos, referencias, cantidades, costes, monedas, duplicados, cola y recuperación |
| Instrumentación en Samsung S25, perfil personal | PASS: cuatro contratos reales preparados; ambos caminos de envío bloqueados antes de subir archivos; historial intacto |
| Revisión visual, emulador API 35 | PASS: selección, búsqueda y estado vacío, cuatro modelos, coste total, teclado, scroll y cuadrícula con estados simulados |
| Accesibilidad de tamaño de texto | PASS: 130 %, CTA visible y último ajuste alcanzable; escala restaurada después |
| Preservación del S25 durante la actualización | 18 archivos de datos idénticos; SQLite integrity_check=ok; 85 generaciones y 84 resultados |
| Generaciones y consumo de saldo durante las pruebas | Ninguno; laboratorio sin permiso INTERNET, cotizaciones simuladas etiquetadas |
| Pixel 8 Pro | Actualización posterior: instalado 1.6.0-compare en usuario 0 por ADB inalámbrico; arranque y navegación visible comprobados. Ver ampliación al final |

El primer arranque en frío del emulador produjo un ANR de System UI. Tras recuperarse, se completaron las pruebas visuales. No es evidencia de rendimiento del Pixel ni una prueba completa de fluidez en el S25. La instrumentación funcional sí se ejecutó en el S25 físico.

## Código principal

- `ui/create/ComparisonPlan.kt`, `ComparisonController.kt`, `ComparisonScreen.kt`.
- `ui/create/GenerationDispatcher.kt`, `CreateViewModel.kt`, `CreateScreen.kt`.
- `core/model/ComparisonRecovery.kt`, `data/repository/Repositories.kt`.
- `ui/generations/GenerationsScreen.kt`, `ui/theme/Theme.kt`.
- `ComparisonPlanTest.kt`, `ComparisonOfflineTest.kt`.

## Límites reales

La ejecución de pago queda sin probar deliberadamente. No se añaden modelos que los proveedores configurados no ofrezcan. Los parámetros compatibles se comparten, pero cada proveedor puede interpretar el prompt de forma distinta. Las cotizaciones que requieren subir archivos permanecen bloqueadas si no hay una tarifa suficiente para calcular el coste previamente.

La comprobación inicial del Pixel quedó bloqueada por ADB. Posteriormente se recuperó el acceso y se aplicaron los ajustes autorizados descritos al final. No se aplicaron al Samsung.

## APK final e instalación

`outputs/comparison-20261001/NEXUS-1.6.0-compare-debug.apk`, versión 1.6.0-compare, código 14. Instalado en el perfil personal del S25 sin desinstalar ni borrar datos. El SHA-256 del APK instalado coincide con el artefacto local:

`ae89b45ea771608bc57508242e496b61161c71aa5f0c47e93927d0ffddb48623`

El Pixel también se actualizó posteriormente, según la ampliación siguiente. `outputs/comparison-20261001/verification-final.json` conserva la evidencia del cierre inicial, anterior a recuperar el Pixel; la compilación final está en `build-polish.log`. Las capturas de flujo preceden únicamente al ajuste de contraste de la barra del sistema; `final-contrast.png` comprueba ese ajuste en el APK final.

## Siguientes mejoras por impacto

1. Cotización completa para modelos con referencias que requieren metadatos del archivo.
2. Presets de comparativa: guardar conjunto de modelos y ajustes compatibles.
3. Comparación de dos resultados a tamaño completo con zoom sincronizado.
4. Duplicar una comparativa anterior como borrador, sin enviarla.
5. Ampliar las comparativas a vídeo con validación de duración y referencias por contrato.

APK, capturas, logs y archivos de resultados están en `outputs/comparison-20261001/`. Los archivos `backup-*.tar`, las bases de datos y las copias de preferencias contienen datos privados y no forman parte de una entrega pública.

## Ampliación: Pixel y recuperación de ADB, 2 de octubre de 2026

- ASUS: corregida la competencia entre un servidor ADB fuera de systemd y `axel-adb.service`. Los clientes de descubrimiento y salud usan ahora el ADB 37 existente y el servidor explícito `127.0.0.1:5037`, sin arrancar otro daemon automáticamente. Copias originales conservadas antes de modificar los tres archivos existentes; no se crearon servicios adicionales.
- Recuperación comprobada tras cambio del puerto inalámbrico y tras reiniciar solamente `axel-adb.service`: Pixel reconectado automáticamente en 2,2 segundos, sin `adb connect` manual. Servicios ADB, descubrimiento y temporizador de salud activos. No se ha probado un reinicio completo del ASUS o del teléfono, ni transiciones entre redes.
- Pixel 8 Pro identificado por modelo y serial; instalación `-r --user 0` de 1.6.0-compare, código 14. Perfil de trabajo sin activar ni instalar Nexus en él. Copia privada de datos previa a la actualización.
- SQLite antes y después: integridad `ok`, 4 generaciones y 4 resultados; todas las filas anteriores conservadas. Preferencias de cuadrícula y apariencia idénticas. Las preferencias cifradas y la marca de actualización del catálogo cambiaron durante el arranque; no se afirma identidad binaria de todos los datos.
- Brillo manual 12/255; tres escalas de animación a 0,5; autorización ADB sin caducidad automática. Se conserva la pantalla encendida mientras carga por AC/USB. Se solicitó a Android cerrar únicamente procesos de fondo seguros de Google TV y Consejos; no se deshabilitaron aplicaciones ni se impusieron límites globales de procesos.
- Bloqueo eliminado mediante Ajustes del perfil personal después de que el usuario introdujera su PIN directamente en el teléfono. Android advirtió de eliminación de biometría y desactivación del pago sin contacto. Verificado `is-user-secure=false` y `get-disabled=true`; pantalla apagada, despertada y Nexus abierta sin PIN.
- Arranque en frío final de Nexus: `Status: ok`, 1716 ms; proceso vivo, cinco destinos de navegación visibles y ningún `FATAL EXCEPTION` en la captura de AndroidRuntime filtrada por su PID. Esto no sustituye una prueba completa de rendimiento o de los formularios en el Pixel.
- Sin generaciones reales ni acciones de envío de pago. La aplicación normal puede actualizar metadatos del catálogo al abrirse.

Evidencia de esta ampliación: `outputs/pixel-link-20261002/reconnection-test.json`, `pixel-final-verification.json`, `data-verification.json`, `preferences-verification.json` y `settings-before.json` / `settings-after.json`. Los archivos TAR y copias SQLite de esa carpeta contienen datos privados.
