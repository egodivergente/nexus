# Inspección visual de APK para agentes

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
