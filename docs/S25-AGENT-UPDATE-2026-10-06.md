# NEXUS Agente: actualizacion S25 pendiente

## Alcance

Paquete objetivo: `com.vidadomu.nexus.agent`, instalado como versionCode 18,
`1.7.0-agent-preview`. Se prepara versionCode 19, `1.7.1-agent-preview`.
No se modifica la instalacion separada `com.vidadomu.nexus`.

## Cambios

- Barra superior de saldos de proveedores configurados con consulta manual.
- Conserva el ultimo saldo conocido si falla la consulta y lo identifica como antiguo.
- Atlas no convierte una respuesta de saldo invalida en cero.
- El teclado Android ofrece Enviar; se bloquea el envio durante carga de adjuntos.

## Evidencia

- PASS: identidad, paquete y version instalada consultados por ADB.
- PASS: certificado del APK instalado cotejado con una clave original existente.
  La clave permanece fuera del repositorio y del ejecutor remoto.
- PASS: diff revisado; instantanea de 381 archivos verificada por el selector.
- FAIL: trabajo ASUS `20261006T043115Z-ebb92aa3`, detenido por reserva de RAM.
- FAIL: trabajo ASUS `20261006T043740Z-c7e1cdb3`, mismo limite con heap de 768 MiB.
- FAIL: trabajo ASUS `20261006T044250Z-797c3b6f`, heap de 512 MiB,
  `OutOfMemoryError: Java heap space` en el compilador Kotlin tras 7m28s.
- PASS: compilacion y ensamblado en Pixel, trabajo `20261006T050951Z-0407afb2`,
  `BUILD SUCCESSFUL in 19m 23s`.
- PASS: 212 pruebas en 35 suites, cero fallos, errores u omisiones.
  Corresponden a `:app:testAgentPreviewUnitTest`; no se ejecutaron las pruebas
  independientes de agent-core ni pruebas instrumentadas.
- PASS: APK e informes recuperados con integridad SHA-256 verificada por el selector.
  APK previo a la firma original: 33077386 bytes.
- PENDIENTE: firma con clave original en el portatil; selector sin ejecutor apto
  por RAM disponible insuficiente en el equipo custodio.
- NO PROBADO: firma final e instalacion nueva.
- NO PROBADO: interfaz, saldos reales y teclado en S25. No se abrio la app ni
  se realizaron generaciones de pago. La instalacion existente no se ha cambiado.

La receta exige ahora mas RAM libre para no repetir intentos insuficientes.
El APK compilado esta recuperado; no hace falta repetir la compilacion.
Reevaluar la receta local de firma cuando el portatil tenga recursos.
Firmar en el custodio de la clave original, verificar certificado,
paquete y version, e instalar con actualizacion conservando los datos.
