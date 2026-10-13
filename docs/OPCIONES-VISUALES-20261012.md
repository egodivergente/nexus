# Opciones visuales y avisos discretos

El formulario del lienzo usa un solo componente compartido para los parámetros de todos los modelos. Cada campo tiene una tarjeta compacta, icono, título y selección. Las listas de hasta seis valores se muestran como botones; las listas más largas conservan el desplegable. Los formatos muestran una miniatura con la proporción real.

Los booleanos opcionales ofrecen Automático, Sí y No: conservan respectivamente la omisión, `true` y `false`. Los obligatorios no permiten omitir el valor. Restaurar el valor predeterminado y consultar el rango quedan dentro de Detalles. Los títulos comunes se muestran en español sin modificar las claves ni los valores enviados al proveedor.

Los avisos compartidos usan una superficie neutra con borde discreto. Conservan el mensaje, sus acciones, el anuncio accesible y los diagnósticos desplegables. El color deja de ser el único indicador del aviso.

La rama conserva la versión instalada 1.9.3 y los cambios posteriores de la rama AKI. No se modifican la persistencia, las credenciales, el envío de generaciones ni su seguimiento.

## Validación

- Se comprobó acceso a ASUSVIDAL, memoria y ambos teléfonos. El ASUS compiló el código Kotlin nuevo, pero su presión de memoria llegó a dejar unos 150 MiB disponibles y dificultó SSH. Se detuvo únicamente el daemon de esta tarea. La compilación continuó en Windows con prioridad baja, dos procesadores y un trabajador; sin paralelismo entre tareas de Gradle.
- `:app:testAgentPreviewUnitTest`: 276 pruebas, cero fallos, errores u omisiones. Incluyen las pruebas existentes de presentación de parámetros, contratos y avisos.
- Compilación completa `:app:testAgentPreviewUnitTest :app:assembleAgentPreview :app:assembleOfflineQa :app:assembleOfflineQaAndroidTest`: `BUILD SUCCESSFUL`, 115 tareas ejecutadas. También compila el nuevo paquete de pruebas de interfaz. Se usaron `--offline --no-daemon --max-workers=1 --no-parallel`, compilador Kotlin dentro del proceso, heap de 1536 MiB y `-PnexusUiQa`.
- APK AgentPreview generado: `com.vidadomu.nexus.agent`, código 37, `1.9.3-agent-preview`. Se conservan la identidad y versión de la base integrada.
- Firma comprobada frente a la APK instalada en el S25: certificado SHA-256 `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`. Se utilizó la clave existente fuera de Git. Los archivos internos del APK coinciden antes y después de firmarlo.
- APK entregable: `outputs/visual-options-20261012/NEXUS-Agent-opciones-visuales.apk`, SHA-256 `41902c9adec2d2746a8a3ddaefcca7c650d40ba23af974558958e901baef9720`. Los outputs privados, claves y cachés no forman parte del commit.
- `ParameterControlsUiTest` cubre las tres selecciones booleanas, restauración, booleanos obligatorios, conservación de proporciones nativas y expansión de diagnósticos. La ejecución y captura física están pendientes: el Pixel permanece bloqueado y se ha pedido al usuario que lo desbloquee.
- No se ha instalado este APK sobre Nexus ni realizado llamadas de generación o de LLM de pago.
