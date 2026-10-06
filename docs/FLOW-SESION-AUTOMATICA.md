# Renovación de la sesión de Flow

Nexus comprueba la sesión al abrir una ventana y después cada cinco minutos mientras está abierta. Al recuperar el foco consulta el estado compartido. Las ventanas usan el mismo comprobador en segundo plano; no abren un navegador por ventana. Los fallos transitorios se reintentan en una consulta posterior y las peticiones manuales tienen una separación mínima de quince segundos.

El comprobador reutiliza exclusivamente el perfil privado `nexus`. Abre Flow sin una ventana visible y, cuando es necesario, sigue su control de acceso de Google y elige únicamente la identidad ya guardada en ese perfil. Para confirmar la conexión exige una pantalla de trabajo de Flow, las cookies de sesión pertinentes y una respuesta autenticada del servidor de Google. La presencia de cookies por sí sola no equivale a una conexión.

Si Google pide contraseña, verificación o autorización, Nexus muestra `Iniciar sesión` y el botón `Conectar`. Ese botón abre el acceso normal de Google en el mismo perfil. El usuario completa los controles de Google; Nexus detecta el resultado y continúa las renovaciones posteriores. No se leen ni introducen contraseñas, no se trasladan cookies desde otros navegadores y no se aceptan acuerdos automáticamente.

El puente renueva la sesión antes de leer los ajustes de una generación nueva. Una lectura que encuentra una sesión caducada permite una sola renovación y repetición de esa lectura. Esta lógica nunca repite el envío de una generación, escalado o exportación. Se conservan las reservas e identificadores anteriores. Si un trabajador de Flow ocupa el perfil, la comprobación de Nexus espera a otra consulta; el bloqueo entre procesos protege el mismo perfil frente a otras herramientas.

## Comprobación del 6 de octubre de 2026

- Pruebas de renovación, concurrencia, identidad de destino, rechazo de una sesión no verificada, errores y repetición exclusiva de la lectura: nueve pruebas unitarias.
- Regresión de interfaz: estados de conexión, botón de acceso y pérdida de conexión, junto con consultas de saldos compatibles.
- Regresión de reservas del proveedor: seis pruebas; un identificador existente no provoca otro envío.
- Prueba real: el navegador del perfil privado siguió el acceso de Flow automáticamente y Google solicitó identificación (`auth_required`, `google_signin`). No se declara recuperada la conexión ni se ha completado el acceso interactivo del usuario.
- Se actualizó el host local en su puerto existente cuando el agente estaba libre y no había trabajos Flow pendientes. Los hashes del historial, credenciales cifradas y ajustes del asistente se conservaron.

No se enviaron generaciones ni operaciones de pago, ni se compiló o empaquetó una entrega. El mecanismo está instalado en este PC. El estado de conexión es independiente del saldo y de la lectura de los ajustes de generación.
