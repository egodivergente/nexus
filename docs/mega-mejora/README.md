# Mejora múltiple NEXUS · 20-10-2026

Encargo: implementar progresivamente V01–V20, U01–U20, D01–D10, H01–H10, P01–P12 y W01–W08. Conservar logos, personalización, funciones existentes y datos. Cada mejora se cierra con evidencia, no por haber escrito código.

## Tanda 1 en curso

- Agente/proyectos: V01–V04, U19.
- Parámetros: V08–V10.
- Catálogo/marcas: V12, V14–V16.
- Lienzo/selector: V05–V07, V11, V13, U01.
- Conflictos Android: W01 y parte disponible de W02.
- Biblioteca/Historial: V17–V18, U10 y U20.
- Accesibilidad puntual en catálogo: D04 parcial.

`estado.json` es el registro por ID. En desarrollo NO significa implementado ni probado. Pruebas humanas de tiempos, TalkBack, háptica y ergonomía no se marcarán como realizadas desde código o CI.

## Bases y salvaguardas

Android parte de `517d707` (vc97). Windows requiere su rama de continuidad `b236cae`, aún no integrada en esta tanda. No fusionar árboles divergentes a ciegas. Rama propia `aki/mega-mejora-20261020`. No generaciones de pago, llamadas a modelos reales ni mutaciones de proyectos reales. No instalaciones sobre las apps del usuario sin confirmación. CI y pruebas usan fixtures y paquete aislado.

AXELPC aparece desconectado de Desktop Commander al inicio. El portátil local no tiene toolchain Android17/JDK17 disponible en las rutas comprobadas. El CI existente está bloqueado por facturación/límite de cuenta (anotación de job 118555144973); no se cambió la cuenta. Como último recurso se usará el SDK existente de ASUS, un trabajador, sin paralelismo y heap limitado. Las comprobaciones visuales físicas seguirán en Pixel cuando se autorice instalar el paquete de pruebas.

## Checkpoint de desarrollo

Código Android de prueba: vc98 / 1.9.64-mega1-agent. El checkpoint `fc9b698` está compilado e instalado **solo como `.megaqa` + `.megaqa.test` en el Pixel 8 Pro**. Producción conserva vc96 / 1.9.62-background-preview; no se ha sustituido. Catálogo, marcas, Biblioteca e Historial integrados no equivalen a la totalidad de las 80 propuestas. Los parches posteriores de memoria, movimiento D08, Ajustes V19, contraste V20, precio reactivo y el arnés requieren otro build; ninguno está incluido en la APK instalada.

## Límites y pendientes de este lote

- Build ASUS `fc9b698`: `EXIT=0`, núcleo 305 PASS + 4 omitidos; app 490 PASS. Total: 795 PASS, 4 omitidos, 0 fallos/errores. Informe por clase y hashes: [evidence/fc9b698-unit.json](evidence/fc9b698-unit.json).
- Compilados APK offlineQa, androidTest y agentPreview (este último sin firma, no instalable como actualización). Instrumentación **no ejecutada**. El Pixel está bloqueado/apagado y se ha pedido desbloquearlo. El CI sigue bloqueado por facturación (job 118555144973).
- `.megaqa`: ausencia de permiso INTERNET comprobada en APK; firma verificada con apksigner. SHA-256: `e36a366c934df17e8f932e34526a6bbd6deabe6a4bc153f578fbdb203db83f9c`. Paquete de tests solo pide REORDER_TASKS y apunta a `.megaqa`.
- Aceptación humana no realizada: V02 con 5 usuarios, TalkBack, fuente al 130 % y 200 %, y revisión visual en Pixel. El S25 no se manipula en esta tanda.
- Parciales dentro de `codigo_pendiente_validacion`: D08 (solo bucles propios; otros indicadores no adaptados), W02 («conservar ambas» no implementado), V14, V15, V16 y D04 (solo accesibilidad del catálogo). V13 está limitado al selector nuevo: una única lista desplazable con insets de Material y pie de 24 dp. Falta verificar teclado/barra gestual y extenderlo a todas las hojas.
- V18: el saldo y el recuento de archivos están en una línea secundaria bajo la barra de Biblioteca, no en un menú.
- Riesgos conocidos sin corregir:
  - `fc9b698` aún tiene el riesgo de borrador fantasma por normalización. El parche posterior valida antes de guardar, conserva notas nuevas y compara cambios por campo para evitar sobrescrituras; pruebas nuevas escritas, sin ejecutar y pendientes de revisión/build;
  - el arnés `UiAuditScreenshotsTest` está adaptado al editor de memoria, al paquete .megaqa y a escalas solicitadas hasta 200 %, todavía sin ejecución; la escala dentro de cada ventana modal debe medirse, no inferirse de la exterior;
  - Atrás cierra la hoja de proyecto y conserva el borrador sin preguntar; el parche en revisión protege el guardado en curso, pendiente de pruebas;
  - coste del historial: ahora usa el componente `ConfirmedGenerationCost` sin maxLines ni ellipsis; el nuevo test a 200 % no se ha ejecutado;
  - el Historial calcula las variantes una vez por snapshot O(n log n), pero `GenerationSummaryRow` conserva el cálculo de conflictos locales O(n) por fila;
  - la alternativa a Alexia solo abre copias locales fiables (incluidas rutas absolutas sin esquema); faltan pruebas reales del selector y de rutas admitidas por FileProvider.
- Pruebas físicas planificadas en Pixel: [MATRIZ-PIXEL.md](MATRIZ-PIXEL.md) (todas pendientes).
- El cambio de estado a completado exige evidencia de pruebas ejecutadas y de dispositivo; no se ha registrado ninguna.

## Verificación

Revisar diff, ejecutar unitarias y build; ejecutar instrumentadas con fixtures aislados. Registrar SHA y URLs de CI. Nunca sustituir una firma privada por la de CI ni presentar un APK sin firma como actualización instalable.

## Turnos de build y siguiente verificación

- ASUS se cede temporalmente al hilo VidalFinanzas, con checkout aislado. Su build continúa RUNNING y ha anunciado una compilación incremental; no iniciar otro Gradle hasta recibir su aviso de liberación.
- El parche de fusión por campos y compatibilidad de borradores está escrito; la protección del guardado asíncrono sigue en revisión. No atribuirles los 795 PASS de `fc9b698`.
- El arnés MegaPresentationMatrixTest registra la escala real del texto sonda y las brechas modales también en modo diagnóstico; aún no compilado ni ejecutado.
- 28 IDs con código (25 en fc9b698, D08, V19 y V20 solo en fuentes posteriores), 52 pendientes; **0 cerrados por aceptación completa**. D08, V13, V14, V15, V16, V19, D04 y W02 son expresamente parciales.
