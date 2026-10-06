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
- NO PROBADO: pruebas unitarias, ensamblado final, firma final e instalacion nueva.
- NO PROBADO: interfaz, saldos reales y teclado en S25. No se abrio la app ni
  se realizaron generaciones de pago. La instalacion existente no se ha cambiado.

La receta exige ahora mas RAM libre para no repetir intentos insuficientes.
Reejecutar plan y submit cuando haya un ejecutor compatible con recursos.
Tras compilar, firmar en el custodio de la clave original, verificar certificado,
paquete y version, e instalar con actualizacion conservando los datos.
