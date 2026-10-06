
# Continuidad entre dispositivos — migración 2026-10-06

Leer CONTINUAR.md y MIGRATION-STATUS.json antes de modificar. El estado de última versión debe comprobarse, nunca inferirse de la fecha o de un APK. Consultar cambios remotos al empezar y guardar los cambios de cada tarea en una rama propia. Subir trabajo pendiente con su estado explícito. No sobrescribir ramas ajenas ni forzar pushes. Mantener secretos y firmas fuera de Git. Aplicar ecosystem-build antes de compilar o empaquetar. No instalar, borrar datos ni ejecutar operaciones de pago por el hecho de compilar.

## GitHub: actualización obligatoria

Al finalizar cualquier tarea que modifique código, configuración o documentación versionable, revisar el diff, crear el commit y publicarlo en la rama adecuada mediante push o el flujo de PR del proyecto. El usuario autoriza esta actualización rutinaria; no pedir confirmación de nuevo. Verificar que GitHub contiene el commit publicado y comunicar su enlace o SHA. Si no hay cambios, comprobar la sincronización sin commits vacíos. Conservar cambios de otras tareas y el historial remoto. Incluir solo los archivos del encargo; excluir credenciales, claves, backups, cachés y outputs privados. No usar force-push.
