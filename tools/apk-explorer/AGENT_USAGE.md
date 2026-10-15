# Inspección visual de APK para agentes

## Auditoría antes/después con la APK nativa 0.2.0

Usa una referencia exportada por la app Android. El agente puede repetirla sin
operar su interfaz ni instalar nada (ADB autorizado y serial explícito):

```powershell
python tools/apk-explorer/replay_capture.py antes.zip --serial SERIAL --out despues.zip --adb RUTA_ADB
python tools/apk-explorer/visual_audit.py antes.zip despues.zip --out auditoria-nueva
```

`replay_capture.py` usa por defecto la APK compilada en `android/app/build/outputs/`;
`--apk` permite especificar otra. Ejecuta su motor en una pantalla virtual y retira
los ficheros temporales. Devuelve JSON con estado y evidencia; salida 2 si el replay
no terminó. No sustituye una app instalada, ni actúa sobre la pantalla física.

`visual_audit.py` requiere Pillow y una carpeta de salida nueva. Empareja por prefijo
de recorrido, no por posición ni parecido visual. Produce `comparison.json`,
`index.html`, pares PNG y diferencias, y `AUDIT.md` con el contrato de revisión.
Un paso ausente, ruta divergente, entorno distinto o replay incompleto queda marcado.
Las métricas miden píxeles; no demuestran calidad estética. Las pistas de tamaño
de controles son candidatas, pues el área táctil real puede estar ampliada.

Para cada hallazgo cita paso, captura, elemento y su impacto; vincula archivos
solo después de inspeccionar el código. Tras corregir el diseño, compila y repite
la misma referencia. No des por restaurados datos o contenido remoto. Conserva
estos ZIP como evidencia privada; no los publiques en GitHub.


Para dejar disponible la pantalla física, usa una pantalla virtual:

```powershell
python tools/apk-explorer/explorer.py --inspect com.empresa.app --virtual --json
```

Este modo navega sin reiniciar la app. Revisa `isolation` en el manifiesto y
[VIRTUAL_DISPLAY.md](VIRTUAL_DISPLAY.md). Comparte los datos de la app instalada;
no presupongas compatibilidad universal ni aislamiento completo de sus servicios.

Si tienes las fuentes y quieres evaluar cómo se renderiza el código, usa
`python tools/apk-explorer/explorer.py --render RUTA_PROYECTO --json`.
Consulta [renderizado desde fuentes](SOURCE_RENDER.md) para el contrato, estados
registrados, datos de prueba y requisitos. Ese modo no usa el móvil. Los comandos
siguientes inspeccionan una app ejecutándose en Android.

Desde la raíz del proyecto:

```powershell
python tools/apk-explorer/explorer.py --inspect "C:\ruta\mi-app.apk" --json
```

Para una app ya instalada, sin reinstalarla:

```powershell
python tools/apk-explorer/explorer.py --inspect com.empresa.app --json
```

El modo `--inspect` detecta el package del APK con aapt, reutiliza las preferencias
de conexión y descubre el serial vigente. No requiere GitHub CLI para una APK local
o una app instalada. Usa exploración por anchura, 12 estados, 40 acciones y 3 minutos
por defecto; los límites incluyen la recuperación de rutas y se pueden ajustar.
Para empezar desde un estado reproducible, reinicia el proceso de la app al iniciar
el modo rápido y al recuperar rutas; no borra sus datos. `--capture-only` evita ese reinicio.
El tiempo de descarga, instalación o lectura del código queda fuera de esos 3 minutos.

El APK indicado se instala con `-r`, conservando datos y comprobando la firma mediante
el instalador Android. El paquete instalado no se reemplaza si solo se indica su nombre.
No instala SDK, ADB, Pillow ni otras dependencias automáticamente.

Para una APK de GitHub, usando el repositorio recordado:

```powershell
python tools/apk-explorer/explorer.py --inspect --release latest --json
```

Si hay varios APK, usar `--asset nombre.apk`. `--repo propietario/repo` permite cambiar
el repositorio. `--source-map --ref rama` añade candidatos del código, sin ejecutarlo.

## Contrato de salida

- `stdout`: un único objeto JSON. El progreso sale por `stderr`.
- `overviews`: láminas JPG, hasta 9 capturas numeradas por lámina; abrirlas con el visor de imágenes.
- `screens`: originales PNG, XML, rutas, etiquetas visibles y SHA-256 comprobado.
- `pending_actions`: controles accesibles que no llegaron a recorrerse; no incluye rutas ocultas desconocidas.
- `coverage`: estados visitados, acciones, controles omitidos y cobertura explícitamente parcial.
- `status`: `finished_partial`, `limit_reached`, `blocked`, `interrupted` o `error`.
- Salida de proceso: 0 para una inspección parcial normal o limitada; 2 si no se ve
  la app; 1 ante error. Un error de argumentos usa el formato habitual de argparse.

Abrir las láminas primero y los PNG originales para detalles de texto o diseño.
El XML ayuda a localizar elementos, pero no demuestra su apariencia. Las capturas
uniformes se marcan para revisión: pueden estar vacías, protegidas o sin renderizar.
No interpretar una captura negra o un diálogo externo como una interfaz validada.
No afirmar cobertura completa ni que todos los controles pendientes sean seguros.

Para volver a revisar una sesión ya generada sin tocar el móvil:

```powershell
python tools/apk-explorer/explorer.py --bundle "C:\ruta\sesion" --json
```

Las láminas requieren Pillow (`python -m pip install Pillow`). Si falta, el agente
recibe los PNG originales y una nota de que no se pudo generar la lámina. El informe
no contiene una crítica visual generada por IA: aporta la evidencia para que el agente
la observe y razone. No hace llamadas a proveedores de IA ni cobra por capturas.
# Descubrir interfaces desde las fuentes

`python explorer.py --discover RUTA_PROYECTO --json` devuelve un inventario Compose
con archivo/línea, dependencias, rutas literales y escenarios registrados. No necesita
ADB, perfil compatible ni compilación. `--output CARPETA` guarda JSON y un informe HTML.
La ventana ofrece la misma operación con **Descubrir pantallas**.

Usar `readiness` para preparar escenarios y `render.json` para conocer la captura real.
No interpretar `registered` o `preview_candidate` como prueba de renderizado: la
detección es estática, admite omisiones y no enumera automáticamente todos los estados.
Los perfiles NEXUS son ejemplos opcionales seleccionados mediante --render-config.
Para otras apps, --project prepara previews Compose y layouts XML automáticamente.

## Entrada general para agentes

```powershell
python explorer.py --project propietario/repositorio --json
python explorer.py --project "C:\proyectos\OtraApp" --project-run --json
```

El primer comando analiza sin compilar. El segundo ejecuta los perfiles elegibles.
Para una APK, `--project-run` delega en `--inspect`. Un `needs_adapter` o un escenario
pendiente no debe comunicarse como una captura completada. Consulta SOURCE_RENDER.md
para los límites concretos. Los escenarios automáticos no dependen de paquetes NEXUS.
