# Continuidad Nexus Windows y moviles

Drive conserva el intercambio existente de proyectos, memoria, galeria,
generaciones, chats como historial inerte y montajes Corte. El complemento
`desktop/device_continuity.py` comparte skills personales, ajustes compatibles,
claves de proveedores y sesion AGY con los dos telefonos, sin subir secretos a
Drive. Usa el protocolo AES-256-GCM de continuidad Android y guarda clave de
vinculacion, bases y conflictos sensibles con Windows DPAPI.

En Ajustes de Windows, Continuidad con tus moviles permite editar:
activacion, lista de direcciones, direccion publica privada del PC, puerto,
clave, intervalo, espera de red (0 ilimitado), MB por archivo (0 ilimitado),
carpeta local AGY y cada categoria. Las eliminaciones son opcionales y no pisan
una edicion local concurrente. Deben estar abiertos Nexus Windows y la
continuidad habilitada en los telefonos. La APK permite desactivar el intercambio
Windows independientemente de la conexion entre moviles.

El PC solicita una devolucion mediante POST /sync cifrado y autenticado.
El telefono solo permite volver a la misma IP de quien firma la solicitud;
la operacion se encola sin sustituir la pareja S25-Pixel. Los errores de una
pareja no impiden procesar las otras. Las solicitudes de red de Windows no
retienen el bloqueo de turnos del agente. Los planes y autorizaciones de chats
no se ejecutan al importar.

Los ajustes propios de plataforma (rutas de puentes, proyecto abierto) siguen
locales. Se traducen las claves nativas a los almacenes que usa Windows,
incluidos Bedrock, cerebros y proveedores de medios personalizados. Los perfiles,
modo, modelo, region y limites compatibles se traducen sin copiar rutas localhost
de un telefono al PC. La sesion de AGY se trata como una revision de dos archivos;
sus versiones conservadas permanecen en DPAPI y los archivos de uso de la CLI
estan en la carpeta que el usuario configura.

Dependencia de Windows: `desktop/requirements-continuity.txt`. El Python firmado
del PC dispone de cryptography 50; no se instala software dentro de Termux.

Validacion de fuente: 21 pruebas de continuidad PASS (cifrado Node/Android,
tramas alteradas/truncadas, HTTP cifrado, claves existentes, limites/perfiles,
conflictos y retorno de skills), 6 pruebas del host/DPAPI PASS; sintaxis Python/JS
PASS. La validacion fisica y activacion se registran al cerrar la integracion;
la APK Android se construye e instala una sola vez con la entrega Studio vc96.

Comprobacion de red previa: el PC descifra el manifiesto real S25 y S25 puede
conectar al puerto Windows 8797. Pixel no estaba disponible por ADB/Tailscale en
ese instante. Vinculacion preconfigurada en el perfil real con DPAPI, todavia
desactivada hasta la activacion coordinada del host. No se han alterado las
credenciales existentes de proveedores ni el respaldo Drive durante esta prueba.

La entrega Windows se identifica como 2026.10.20-device-continuity.2; su paquete
incluye las 15 bibliotecas JVM ya instaladas y verificadas byte a byte, sin
reconstruir ni sustituir el motor Corte/Drive durante el desarrollo del enlace.

Pixel reconectado: verificados tambien manifiesto cifrado Pixel y retorno TCP
desde Pixel al puerto8797 del PC. Ambos enlaces de red estan comprobados.
La integracion conserva la renovacion de token Drive posterior a preparar
originales del commit32d1595; 11 pruebas Drive PASS.

## Validacion del paquete final Windows .2

Runtime completo compilado con JDK17 y 1 worker, para mantener compatibilidad
binaria entre DesktopAgent y el nucleo ampliado. JVM: 394 PASS, 4 omitidas;
Python: 21 continuidad + 6 host/DPAPI + 11 Drive PASS. Incluye edf8d7c+9fda60:
solo se consideran equivalentes referencias con prueba de bytes SHA; lo
no demostrado sigue siendo conflicto. Fuente JSON movil corregida en239df6f y
ad5a6ec: strings y contenedores se codifican como JSON portable estricto.

La activacion .1 detecto que el indice vivo previo tenia4proyectos/917assets/
524generaciones, mientras el archivo anterior conservaba2/384/282. Se restauro
el snapshot vivo exacto capturado antes del cambio con ambos sync pausados,
se comprobo retencion de todos los IDs y persistencia real mediante una escritura
sin cambios de un asset existente. No se atribuye una causa sin evidencia.
No se cancelaron ni reenviaron los dos registros PROCESSING importados de Android.

Cierre de equivalencias incluye16bbcde: mismo puntero URI en el mismo proyecto
se oculta como copia sin eliminar IDs, historial ni relaciones. Pruebas finales
JVM394PASS4omitidas; Python21continuidad+6host+11DrivePASS.
