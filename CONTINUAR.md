# Continuar nexus-pixel-v17

Estado: **VARIANTE_PIXEL_RECUPERADA_PENDIENTE_RECONCILIACION**.

Fuente v17 recuperada directamente del Pixel. Preservar la variante y comparar antes de combinarla con la fuente Android v18. S25 privado pendiente.

Fuente: Pixel 8 Pro / Termux SSH verificado: /data/data/com.termux/files/home/nexus-build/batches-20261002/source

Esta copia conserva el código recuperado y un manifiesto SHA-256. No se ha recompilado durante la migración. Los datos de las apps, claves y artefactos generados se conservan en sus ubicaciones originales; no están incluidos aquí.

El S25 estaba desconectado al iniciar la comparación; el acceso ADB del Pixel no permitió leer su Termux privado. Hasta comprobar esos ámbitos no se garantiza que ésta sea la última fuente de todo el ecosistema.

Antes de continuar: recuperar cambios del repositorio, comprobar la rama y leer AGENTS.md. Al terminar: commit y push de la rama, documentar pruebas y trabajo pendiente. Para Android, verificar el certificado de la app instalada antes de preparar una actualización.
