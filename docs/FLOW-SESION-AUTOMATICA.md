# Renovación de la sesión de Flow

Nexus comprueba la sesión al abrir una ventana y después cada cinco minutos mientras está abierta. Al recuperar el foco consulta el estado compartido. Las ventanas usan el mismo comprobador en segundo plano; no abren un navegador por ventana. Los fallos transitorios se reintentan en una consulta posterior y las peticiones manuales tienen una separación mínima de quince segundos.

El comprobador reutiliza exclusivamente el perfil privado `nexus`. Abre Flow sin una ventana visible y, cuando es necesario, sigue su control de acceso de Google y elige la identidad guardada en ese perfil. Si falta el marcador de identidad, consulta la cuenta autenticada del mismo perfil o selecciona la única cuenta ofrecida por Google; no adivina entre varias cuentas. Puede avanzar el identificador conocido, nunca la contraseña. Si Google rechaza el navegador sin interfaz, reintenta una sola vez con el mismo navegador en modo normal fuera de pantalla, después de cerrar el primer contexto y liberar el perfil. Para confirmar la conexión exige una pantalla de trabajo de Flow, las cookies de sesión pertinentes y una respuesta autenticada del servidor de Google. La presencia de cookies por sí sola no equivale a una conexión.

Si Google pide contraseña o verificación, Nexus identifica ese requisito y mantiene el botón `Conectar`. También distingue la selección de cuenta pendiente y el rechazo del navegador. Ese botón abre el acceso normal de Google en el mismo perfil. El usuario completa los controles de Google; Nexus detecta el resultado y continúa las renovaciones posteriores. No se leen ni introducen contraseñas, no se trasladan cookies desde otros navegadores y no se aceptan acuerdos automáticamente.

El puente renueva la sesión antes de leer los ajustes de una generación nueva. Una lectura que encuentra una sesión caducada permite una sola renovación y repetición de esa lectura. Esta lógica nunca repite el envío de una generación, escalado o exportación. Se conservan las reservas e identificadores anteriores. Si un trabajador de Flow ocupa el perfil, la comprobación de Nexus espera a otra consulta; el bloqueo entre procesos protege el mismo perfil frente a otras herramientas.

## Comprobación del 6 de octubre de 2026

- Pruebas de renovación, concurrencia, identidad de destino, rechazo de una sesión no verificada, selección de cuenta única, separación de contraseña/verificación, reintento limitado del navegador y repetición exclusiva de la lectura: quince pruebas unitarias.
- Regresión de interfaz: estados de conexión, botón de acceso y pérdida de conexión, junto con consultas de saldos compatibles.
- Regresión de reservas del proveedor: seis pruebas; un identificador existente no provoca otro envío.
- Prueba real de conexión automática: el perfil privado alcanzó el espacio de trabajo de Flow y superó la comprobación autenticada del servidor (`connected`). No se pulsó el botón `Conectar` ni se introdujo una contraseña durante esta recuperación.
- Lectura real posterior mediante el puente de Nexus: ajustes de Nano Banana 2, formato 1:1, con respuesta de Flow de cero créditos. No se envió una generación.
- Se actualizó el host local en su puerto existente cuando el agente estaba libre y no había trabajos Flow pendientes. Los hashes del historial, credenciales cifradas y ajustes del asistente se conservaron.

No se enviaron generaciones ni operaciones de pago, ni se compiló o empaquetó una entrega. El mecanismo está instalado en este PC. El estado de conexión es independiente del saldo y de la lectura de los ajustes de generación.
