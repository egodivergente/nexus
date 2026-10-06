# Incidencia de inicio de Google Flow · 6 de octubre de 2026

En la instalación local, la barra de saldos mostraba `Google Flow · No disponible` porque `balanceRoutes` no implementa una consulta de saldo para este proveedor. La etiqueta no se calculaba a partir de su autenticación o conectividad. Se cambia a `Saldo sin consultar` y se aclara su alcance en el texto de ayuda; no se inventa un saldo ni una conexión operativa.

La comprobación se realizó contra el host utilizado por el acceso directo de Windows, con sus datos reales. El inicio devolvió cuatro proveedores configurados y cinco modelos Flow. Tras actualizar la página, se comprobó el nuevo texto en ese mismo host. No hubo compilación ni empaquetado.

Una consulta real `flowQuote` para Nano Banana 2, formato 1:1, falló. El diagnóstico del puente encontró una redirección desde el proyecto de consulta a `https://flow.google.com/about`. La comprobación MCP `gflow_auth_status`, en el perfil `nexus`, devolvió `google_session_only`, con HTTP semántico 401: hay sesión de Google, pero el comprobador no confirma una sesión válida de Flow. Estos resultados no demuestran por qué Google rechazó el acceso. La renovación del acceso a Flow queda pendiente; no se declara la generación operativa.

La regresión `node desktop/tools/test-balance-availability.js` verifica el arranque con Flow configurado, la actualización de un saldo compatible y un fallo realista de consulta. La ausencia de un endpoint de saldo no convierte al proveedor en «No disponible»; los fallos de consultas implementadas se siguen mostrando. No hay llamadas de red en esa regresión.

No se enviaron prompts, generaciones, escalados ni operaciones de pago. Se conservaron credenciales, perfil e historial. La auditoría visual anterior bloqueaba las consultas a proveedores; ese resultado no probaba el saldo ni la autenticación vigente de Flow.
