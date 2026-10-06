# Nexus Desktop — entrega Windows, 2 de octubre de 2026

Estado: compilado; ejecución nativa BLOQUEADA por Smart App Control por falta de firma de editor confiable. No se ha desactivado ni alterado la protección de Windows. No es una entrega validada de extremo a extremo ni se ha demostrado aún paridad completa con Android.

## Origen y adaptación
Se inspeccionó Nexus 1.5.1-refined, código 13, instalado en el Samsung SM-S938B, perfil personal 0, y el proyecto Android existente. Se creó una aplicación Windows x64 C#/.NET Framework con WebView2. Usa el motor instalado de Windows; no incorpora un emulador Android ni otro Chromium completo.

Interfaz adaptada al escritorio: barra lateral, catálogo por familias, búsqueda, filtros de proveedor, favoritos y modelos ocultos, densidad compacta, orden por lanzamiento conocido, lienzo con formulario desplazable y botón Generar independiente, presets, historial y biblioteca, tema carbón/negro y acentos. Comparación de 2 a 6 modelos con prompt compartido, adaptación explícita de parámetros y revisión del coste antes del envío.

838 registros del catálogo real, incluidos Atlas Cloud, Runway y Crun. Las fechas desconocidas permanecen vacías. El catálogo es una instantánea del móvil, no una garantía de disponibilidad actual de cada endpoint. Las tarifas base no equivalen al total: Generar requiere estimación vigente.

## Datos y credenciales
Se trasladaron automáticamente las tres credenciales del Samsung y se verificó su cifrado/descifrado mediante Windows DPAPI CurrentUser. Se guardan en %LOCALAPPDATA%/NexusDesktop/credentials.dpapi. No están en el EXE, el ZIP, el código ni la interfaz web. No hay que volver a escribirlas en este usuario de Windows.
Se importaron 85 tareas y 84 referencias de resultados del historial. Los originales del móvil se conservaron. Los archivos multimedia descargados solo en Android NO se han copiado; se mantienen las URLs del historial. La migración no es sincronización entre dispositivos. Los presets Android y todas las preferencias avanzadas no se han migrado.

## Comprobaciones
- Compilación Windows x64: PASS, usando el compilador .NET instalado (~2 segundos).
- ASUSVIDAL: 20 pruebas del motor y sintaxis JS, PASS, 0 llamadas de red en las pruebas. Incluyen normalización, requisitos, MIME, payloads exactos, Motion Control, comparación, tarifas y campos peligrosos.
- Interfaz: revisión en Edge con puente QA aislado, sin claves ni historial real. Navegación, formulario, coste simulado, comparación y bloqueo del envío de laboratorio comprobados. Esta prueba NO valida el host .NET.
- Host Windows: NO PROBADO en ejecución; Smart App Control rechaza Nexus.exe sin firma. El registro CodeIntegrity confirma el bloqueo. No hay certificado de firma de código en el almacén del usuario.
- Generaciones, subidas de medios y llamadas de pago durante el trabajo: 0. No se consumió saldo.

## Bloqueo y pendientes reales
Hace falta firmar el ejecutable con un certificado de editor que Smart App Control reconozca o completar una distribución oficial firmada. No se ha comprado un certificado, contratado un servicio de firma ni publicado en Microsoft Store.
Después de resolver la firma quedan por comprobar el arranque WebView2, selector nativo de archivos, acceso DPAPI desde el host, cotizaciones y respuestas reales de los proveedores sin generación. No se han medido memoria, CPU ni tiempos del proceso nativo. La optimización actual es arquitectónica, no un benchmark demostrado.
La compatibilidad de los 838 modelos no ha sido probada individualmente. Los esquemas incompatibles o precios desconocidos bloquean el envío. Audio y chat requieren más validación. La biblioteca muestra por ahora la primera salida de cada tarea. El catálogo no se actualiza automáticamente. No hay paridad total con Android en gestos, reordenación, todas las personalizaciones y manejo multimedia.

## Archivos
Nexus.exe, DLL de WebView2 y carpeta ui deben conservarse juntos. No es un ejecutable autónomo de un solo archivo. Requiere Windows x64, .NET Framework compatible y WebView2 Runtime (detectados en este EliteBook).
Código: desktop/src/Program.cs, desktop/ui/app.js, desktop/ui/engine.js, desktop/ui/style.css. Compilación: powershell -NoProfile -File desktop/build.ps1. Tests: node desktop/tools/test-engine.js (preferentemente ASUS).
El ZIP solo contiene el programa, recursos públicos y este documento; excluye respaldos privados, estado, credenciales y utilidades de transferencia.

Referencia de firma: https://learn.microsoft.com/en-us/windows/apps/develop/smart-app-control/code-signing-for-smart-app-control

## Revisión del 2 de octubre · catálogo y acabado

La interfaz actual conserva todas las rutas de imagen y vídeo accesibles por familia y búsqueda. Añade una guía de 40 rutas concretas, agrupadas por modelo, sin ocultar automáticamente el resto. Corrige la atribución de Youchuan, precios base sin unidad, menús nativos visibles, textos de pluralización y el inicio del lienzo. La edición local activa se revisó en Edge y las capturas comparativas están en `docs/Desktop-audit-20261002.md` del proyecto. Se compiló de nuevo el host WebView2 con solicitud de barra de título oscura; el EXE sigue sin poder iniciarse en este EliteBook porque Smart App Control bloquea binarios sin firma. La revisión en Edge no demuestra el aspecto ni el funcionamiento del EXE nativo.
