# Nexus Desktop: importación de 4 GiB y agente local

Versión: `2026.10.20-desktop-local-4gb.1`. Se conserva la actualización instalada
de Drive `846227d` (cuenta efectiva y carpeta de sincronización).

## Comportamiento

- Biblioteca incorpora **Importar · hasta 4 GiB**, arrastre y búsqueda de archivos
  importados en la cuadrícula principal. El selector nativo acepta el mismo límite:
  4.294.967.296 bytes por archivo. Los bloques son de 4 MiB; el RPC conserva su
  límite de 20 MiB y su autenticación de sesión. La copia no sube al proveedor.
- La copia comprueba espacio, tamaño, integridad SHA-256 y modificación del origen.
  Solo publica un archivo completo mediante cambio de nombre atómico. Rechaza
  archivos vacíos, bloques fuera de posición y tamaños superiores al máximo.
- El agente conserva las skills de modelos de la APK y añade `windows_studio`.
  Consulta el registro y los esquemas vigentes para modelos, parámetros y costes.
- Herramientas propias de Windows: listar carpetas con filtro y paginación,
  importar rutas reales en un proyecto, abrir archivos/carpetas/programas/URLs y
  ejecutar PowerShell con el usuario actual. Se mantienen los modos del agente:
  Asistente consulta; Copiloto propone cambios; Agente ejecuta las acciones permitidas.
  Abrir un programa confirma la solicitud de apertura, no una inspección visual.
- `detect_video_scenes` utiliza FFmpeg local y produce cambios de plano,
  hojas visuales con tiempos e informe de cobertura. Procesa hasta diez minutos
  por encargo y muestra hasta 24 muestras, con `next_start_ms` para continuar.
  El modelo interpreta las imágenes y puede afinar los límites con fotogramas.
  Los cortes utilizan `edit_video_assets` y conservan original, proyecto y linaje.
- Sol admite hasta 64 herramientas por petición. El adaptador expone grupos
  intercambiables mediante `use_desktop_tools`; archivos, proyectos y Corte
  permanecen disponibles en ambos grupos. Ninguna capacidad se elimina.
- Cambiar de proyecto desde el agente persiste la selección y permite preparar
  Corte en ese mismo turno con la revisión correspondiente.

## Validación en AXELPC

- Copia real de **4 GiB exactos**: tamaño y SHA-256 del original/copia coinciden;
  rechazo del mismo archivo con un byte adicional. Archivo sintético para probar
  tamaño e integridad; no demuestra decodificación de un vídeo de 4 GiB.
- Edge y host HTTP reales, datos aislados: importación de varios bloques desde
  el selector de Biblioteca, indexación, tarjeta de galería, descarga por Range,
  rechazo sin sesión y del tamaño excesivo, sin errores JavaScript.
- Vídeo sintético de tres escenas: detección de cambios a 2 y 4 segundos,
  recorte real de la escena central, duración de 2 segundos y original intacto.
- **Sol real**, conexión existente, perfil de prueba sin proyectos privados:
  creó y activó un proyecto, importó el vídeo, inspeccionó las hojas, afinó con
  fotogramas, recortó la escena azul entre 2 y 4 segundos y abrió el resultado
  en el chat. Doce rondas de herramientas; salida de 2 segundos. Se usó el LLM
  existente; no se invocaron proveedores de generación de medios.
- PowerShell real: salida y código de retorno verificados. La apertura de un
  archivo se comprueba con adaptador simulado; no equivale a validar la interfaz
  de todas las aplicaciones externas.
- Compilación JVM, pruebas de agente/Corte, biblioteca, edición/exportación,
  controles de proyectos y sincronización Drive. Los informes JVM conservan
  las pruebas omitidas preexistentes del core; no se declaran ejecutadas.

Pruebas del encargo:

```powershell
powershell -NoProfile -File desktop/tools/build-windows-corte.ps1
$env:NEXUS_TEST_4GIB='1'
python -X utf8 desktop/tools/test-desktop-local-files.py
python -X utf8 desktop/tools/test-windows-library.py
python -X utf8 desktop/tools/test-windows-agent-providers.py
python -X utf8 desktop/tools/test-corte-windows.py
python -X utf8 desktop/tools/test-corte-ui.py
python -X utf8 desktop/tools/test-project-controls-ui.py
python -X utf8 desktop/tools/test-drive-sync.py
```

La prueba real del modelo demuestra ese caso sintético. Las muestras no
garantizan localizar todas las acciones de cualquier vídeo ni precisión
semántica perfecta. Si falta visión o la escena no es distinguible, el agente
debe indicarlo y pedir el dato necesario.
