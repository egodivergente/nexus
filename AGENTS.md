# NEXUS: compilación y pruebas

Petición del usuario actualizada: priorizar **AXELPC** (`100.72.111.97`) y el dispositivo móvil físico (**Pixel 8 Pro** / teléfono principal), evitando saturar el HP EliteBook y **dejando el servidor ASUS (`ASUSVIDAL`) únicamente como último recurso**.

- **Prioridad 1 — AXELPC**: Si la sesión ya corre en `AXELPC`, ejecutar las compilaciones y cargas pesadas localmente en `AXELPC`. Si se trabaja desde el HP EliteBook, priorizar delegar la compilación a `AXELPC` o al Pixel antes de sobrecargar el portátil.
- **Prioridad 2 — Pruebas y entorno móvil (Pixel 8 Pro)**: Para pruebas Android, priorizar el dispositivo físico conectado por ADB (directamente o por Tailscale). Verificar modelo y transporte actual; no arrancar un emulador local si el teléfono físico cubre la prueba y está disponible.
- **Último lugar — Servidor ASUS (`ASUSVIDAL`, `vidal@100.112.57.2`)**: El servidor ASUS tiene poca RAM (~3,7 GB). **No utilizarlo por defecto para compilaciones ni procesado pesado.** Usarlo únicamente en último lugar si `AXELPC` y las demás opciones no están disponibles o si el usuario lo pide expresamente (`--max-workers=1 --no-parallel`).

Al terminar, recuperar APK e informes y verificar identidad/integridad de los artefactos. Distinguir acceso comprobado, compilación realizada y pruebas ejecutadas.

# Continuidad entre dispositivos — migración 2026-10-06

Leer CONTINUAR.md y MIGRATION-STATUS.json antes de modificar. El estado de última versión debe comprobarse, nunca inferirse de la fecha o de un APK. Consultar cambios remotos al empezar y guardar los cambios de cada tarea en una rama propia. Subir trabajo pendiente con su estado explícito. No sobrescribir ramas ajenas ni forzar pushes. Mantener secretos y firmas fuera de Git. No instalar, borrar datos ni ejecutar operaciones de pago por el hecho de compilar.

## GitHub: actualización obligatoria

Al finalizar cualquier tarea que modifique código, configuración o documentación versionable, revisar el diff, crear el commit y publicarlo en la rama adecuada mediante push o el flujo de PR del proyecto. El usuario autoriza esta actualización rutinaria; no pedir confirmación de nuevo. Verificar que GitHub contiene el commit publicado y comunicar su enlace o SHA. Si no hay cambios, comprobar la sincronización sin commits vacíos. Conservar cambios de otras tareas y el historial remoto. Incluir solo los archivos del encargo; excluir credenciales, claves, backups, cachés y outputs privados. No usar force-push.
