# Descargas de Flow al puente S25

Utilidad local Windows para trasladar las nuevas imágenes y vídeos descargados de Flow a una carpeta compartida. No cambia la configuración del navegador ni necesita claves de Google, SMB o Nexus.

## Instalación

Requiere Python para Windows y carpetas NTFS en el mismo volumen. Ejemplo desde la raíz del repositorio:

```powershell
./desktop/tools/install-flow-download-router.ps1 `
  -Source @("$env:USERPROFILE\Downloads") `
  -Destination "$env:USERPROFILE\Desktop\Puente_S25"
```

La instalación inicial registra los archivos existentes como referencia; solo procesa nuevas descargas o archivos que cambien después. Crea un inicio automático del usuario mediante `Flow a Puente S25.lnk`, sin ventanas de consola. No requiere sesión del agente ni tareas programadas de IA.

La configuración, estado, bloqueo de instancia, estado de salud y registro rotativo quedan en `%LOCALAPPDATA%\PuenteS25\FlowDownloads`. No se incluyen en Git.

## Comportamiento

- Examina los archivos directamente contenidos en las carpetas de origen cada cuatro segundos.
- Solo considera extensiones de imagen/vídeo y metadatos `Zone.Identifier` con origen HTTPS `flow.google.com` o la ruta de Flow en `labs.google/fx/.../tools/flow`. También admite enlaces blob de Flow. No clasifica un archivo solo por su nombre.
- Espera cuatro segundos de tamaño/fecha estables, ausencia de archivos parciales y disponibilidad exclusiva. Los archivos `.crdownload` y `.part` no se trasladan.
- El traslado es una operación de renombrado del mismo volumen. Windows rechaza colisiones y se elige un sufijo numérico, sin reemplazar el archivo existente. Si el volumen difiere, conserva el original y registra el error.
- Restablece la herencia de permisos del archivo trasladado para que la cuenta de la carpeta compartida pueda acceder. Si falla, intenta devolverlo al origen sin sobrescribir otro archivo.
- Los archivos de otros sitios, sin metadatos de origen o fuera de las carpetas configuradas permanecen en su ubicación. No realiza un barrido histórico de descargas antiguas.

Al trasladarse un archivo, el historial del navegador puede conservar su antigua ruta en Descargas; el archivo final se abre desde la carpeta de destino. El acceso por el S25 depende de que el recurso SMB y Tailscale sigan disponibles.

## Verificación realizada

En AXELPC, el 22-10-2026:

- Siete pruebas de integración Windows correctas: integridad, conservación de descargas anteriores, otros orígenes y dominios impostores, URL HTTPS/blob/Flow antiguo, colisiones, descarga parcial/cambiante y archivo bloqueado.
- Proceso instalado iniciado con `pythonw`, estado de salud actualizado e inicio automático registrado.
- Prueba controlada con un PNG sintético y metadatos de descarga Flow: el proceso instalado lo trasladó desde Descargas, conservó el SHA-256 y retiró el origen.
- El PNG trasladado pudo leerse por SMB con la cuenta privada que utiliza el S25. Se retiró únicamente ese archivo de prueba, conservando los archivos del usuario.
- No se ejecutaron generaciones ni descargas de pago. La prueba controlada no se presenta como una nueva exportación real del sitio web de Flow.

Para detenerlo, retirar su acceso directo de Inicio y finalizar únicamente el proceso `pythonw` cuya línea de comandos apunte a esta utilidad. El instalador puede actualizar esa instancia identificándola por PID y ruta, conservando su estado y configuración.
