# NEXUS Agente: actualizacion S25 instalada

## Alcance

Paquete objetivo: `com.vidadomu.nexus.agent`, actualizado de versionCode 18,
`1.7.0-agent-preview` a versionCode 19, `1.7.1-agent-preview`.
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
- PASS: firma original en el portatil mediante `20261006T055357Z-4162777c`.
  Limites verificados: CPU 5 %, memoria del proceso 512 MiB, reserva 1024 MiB.
- PASS: certificado SHA-256 coincidente con el APK anterior:
  `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`.
- PASS: `adb install -r` devuelve Success; Package Manager confirma versionCode 19
  y `1.7.1-agent-preview`, conservando firstInstallTime. No se desinstalo ni borro datos.
- PASS: paquete separado `com.vidadomu.nexus` sigue en versionCode 17 / 1.6.3-results.
- PASS: SHA-256 del APK final:
  `32c7dd72a1e1d2eff8199efc78d85fedd83307dc776acdc0d0a320dad036884e`.
- NO PROBADO: interfaz, saldos reales y teclado en S25. No se abrio la app ni
  se realizaron generaciones de pago. No se inspeccionaron sus datos privados.

## Ajuste del selector

El perfil optativo `resource_profile: apk-sign` se limita a apksigner local Windows,
Java con heap 128 MiB, memoria maxima del proceso 512 MiB y receta de bajo impacto.
Reduce la reserva a 1024 MiB y limita CPU a 5 % tanto en admision como en ejecucion.
La receta local usa `max_cpu_percent: 90`; conserva controles de presion, temperatura,
disco, identidad, bloqueo exclusivo y vigilancia de recursos. No modifica perfiles
de compilacion ni mueve claves. La receta privada de firma queda fuera de Git.

PASS: 43 pruebas de selector (test_router, test_adaptive, test_signing_profile),
incluidas cuatro nuevas para limites, aislamiento del perfil y rechazos.
