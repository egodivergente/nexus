# NEXUS: compilación y pruebas

Priorizar ASUSVIDAL para compilar y el Pixel 8 Pro físico para probar, conforme a la preferencia del usuario de mantener fluido el EliteBook. Comprobar las alternativas antes de ejecutar Gradle o arrancar un emulador en Windows.

## Acceso y herramientas comprobados el 2026-10-01

- SSH existente: `vidal@100.112.57.2`, host `ASUSVIDAL`. Usar las claves existentes y verificar la clave del servidor.
- Java está instalado fuera del PATH habitual: `JAVA_HOME=/home/vidal/aki/toolchain/jdk17` (Java 17 comprobado).
- SDK Android: `ANDROID_HOME=/home/vidal/aki/toolchain/android-sdk`; existen `platforms/android-35` y `build-tools/35.0.0`, coincidentes con el compileSdk actual. Esto verifica herramientas, no una compilación del código actual.
- El ASUS dispone de unos 3.8 GiB de RAM. Comprobar memoria y compilaciones activas; empezar con `--max-workers=1 --no-parallel` y ajustar memoria a los recursos disponibles.
- Existe `/home/vidal/workspace/nexus`, pero su sincronización con este workspace no está verificada. Revisar sus instrucciones y reservas antes de cualquier escritura; no sobrescribir trabajo ajeno ni compilar esas fuentes como sustituto del código actual. Si se necesita una copia temporal de compilación, mantenerla fuera del workspace compartido y registrar su procedencia; excluir cachés, outputs, credenciales y `local.properties` de Windows.
- El ADB del ASUS tiene acceso autorizado al Pixel 8 Pro. Consultar `adb devices -l` allí, comprobar `ro.product.model` y seleccionar explícitamente su serial vigente. El puerto observado fue `192.168.1.146:33303`; no asumir que sigue siendo ese.
- El acceso directo al Pixel desde Windows falló en esta comprobación, mientras que el acceso mediante ASUS funcionó. Probar esa ruta antes de recurrir al emulador.
- Compilación en Pixel verificada el 2026-10-02: Termux nativo, JDK 17 ARM64 y AAPT2 ARM64 aislados, Gradle 8.9 y SDK 35. El usuario pidió usar expresamente el Pixel en esta tarea. Consultar `docs/COMPILAR-PIXEL.md`; preservar los datos de Termux. No usar el JDK 21 ni el AAPT2 antiguos del paquete Google Play para este proyecto.

Al terminar, recuperar APK e informes y verificar identidad/integridad de los artefactos. Distinguir acceso comprobado, compilación realizada y pruebas ejecutadas. Si hay que usar el EliteBook, indicar el impedimento remoto y limitar su carga.

# Continuidad entre dispositivos — migración 2026-10-06

Leer CONTINUAR.md y MIGRATION-STATUS.json antes de modificar. El estado de última versión debe comprobarse, nunca inferirse de la fecha o de un APK. Consultar cambios remotos al empezar y guardar los cambios de cada tarea en una rama propia. Subir trabajo pendiente con su estado explícito. No sobrescribir ramas ajenas ni forzar pushes. Mantener secretos y firmas fuera de Git. Aplicar ecosystem-build antes de compilar o empaquetar. No instalar, borrar datos ni ejecutar operaciones de pago por el hecho de compilar.

## GitHub: actualización obligatoria

Al finalizar cualquier tarea que modifique código, configuración o documentación versionable, revisar el diff, crear el commit y publicarlo en la rama adecuada mediante push o el flujo de PR del proyecto. El usuario autoriza esta actualización rutinaria; no pedir confirmación de nuevo. Verificar que GitHub contiene el commit publicado y comunicar su enlace o SHA. Si no hay cambios, comprobar la sincronización sin commits vacíos. Conservar cambios de otras tareas y el historial remoto. Incluir solo los archivos del encargo; excluir credenciales, claves, backups, cachés y outputs privados. No usar force-push.
