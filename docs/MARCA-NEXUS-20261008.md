# Marca NEXUS Studio — 2026-10-08

Se integra el logo metálico proporcionado por el usuario en la edición de escritorio.

- Barra lateral compacta: símbolo N.
- Barra lateral expandida y bienvenida del asistente: logo completo.
- Avatares y estados vacíos: símbolo N.
- Favicon e icono de Windows: ICO transparente en 16, 24, 32, 48, 64, 128 y 256 píxeles.

El original transparente queda en `desktop/ui/branding/nexus-source.png`. Los píxeles del logo recortado coinciden exactamente con el original; solo se eliminan márgenes transparentes y se redimensiona el símbolo para los iconos.

## Regeneración

Con Python y Pillow instalados, desde la raíz del repositorio:

```powershell
python -B desktop/tools/prepare_branding.py
```

La utilidad produce los PNG de la interfaz y actualiza `desktop/nexus.ico`, `desktop/ui/favicon.ico` y `desktop/ui/branding/nexus.ico`.

## Verificación realizada

- Navegador aislado: símbolo compacto, logo expandido, bienvenida y favicon correctos; anchuras de 1360 y 480 píxeles sin desbordamiento horizontal ni errores JavaScript.
- Host local instalado: los assets servidos coinciden con los archivos preparados.
- Acceso directo existente de Windows: icono actualizado y destino, argumentos y directorio de trabajo conservados. Se notificó el cambio al shell; no se comprobó visualmente su renderizado en Explorer.
- `node --check desktop/ui/desktop-design.js` correcto.
- Sin llamadas de generación de pago.

Las capturas e informes locales están en `outputs/branding-20261008/`, fuera de la publicación. La aplicación abierta muestra la nueva marca al recargarla; no se ha forzado una recarga de la sesión del usuario.
