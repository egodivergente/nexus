# Mejora múltiple NEXUS · 20-10-2026

Encargo: implementar progresivamente V01–V20, U01–U20, D01–D10, H01–H10, P01–P12 y W01–W08. Conservar logos, personalización, funciones existentes y datos. Cada mejora se cierra con evidencia, no por haber escrito código.

## Tanda 1 en curso

- Agente/proyectos: V01–V04, U19.
- Parámetros: V08–V10.
- Catálogo/marcas: V12, V14–V16.
- Lienzo/selector: V05–V07, V11, V13, U01.
- Conflictos Android: W01 y parte disponible de W02.

`estado.json` es el registro por ID. En desarrollo NO significa implementado ni probado. Pruebas humanas de tiempos, TalkBack, háptica y ergonomía no se marcarán como realizadas desde código o CI.

## Bases y salvaguardas

Android parte de `517d707` (vc97). Windows requiere su rama de continuidad `b236cae`, aún no integrada en esta tanda. No fusionar árboles divergentes a ciegas. Rama propia `aki/mega-mejora-20261020`. No generaciones de pago, llamadas a modelos reales ni mutaciones de proyectos reales. No instalaciones sobre las apps del usuario sin confirmación. CI y pruebas usan fixtures y paquete aislado.

AXELPC aparece desconectado de Desktop Commander al inicio. El portátil local no tiene toolchain Android17/JDK17 disponible en las rutas comprobadas. El CI existente está bloqueado por facturación/límite de cuenta (anotación de job 118555144973); no se cambió la cuenta. Como último recurso se usará el SDK existente de ASUS, un trabajador, sin paralelismo y heap limitado. Las comprobaciones visuales físicas seguirán en Pixel cuando se autorice instalar el paquete de pruebas.

## Checkpoint de desarrollo

Código Android de prueba: vc98 / 1.9.64-mega1-agent. No instalado. El catálogo/marcas sigue en trabajo paralelo y se incorporará en el siguiente checkpoint. Se ha creado el paquete aislado `.megaqa`. No se declara compilación ni ejecución hasta disponer de sus informes.

## Verificación

Revisar diff, ejecutar unitarias y build; ejecutar instrumentadas con fixtures aislados. Registrar SHA y URLs de CI. Nunca sustituir una firma privada por la de CI ni presentar un APK sin firma como actualización instalable.
