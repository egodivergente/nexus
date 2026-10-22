# Flow: recuperación autónoma y seguimiento visible

Android 1.9.69-agent-preview / vc103, sobre la corrección de referencias de
`611dfe7`. Rama `codex/flow-autonomy-progress-20261022`.

## Comportamiento

- El POST de un pedido reserva su identificador y responde mientras la preparación
  continúa en una tarea del puente. Consultar ese identificador durante el acceso
  a Google devuelve el estado guardado sin esperar al bloqueo del navegador.
- Fases: recibido, recuperación de sesión, preparación, referencias, envío y
  espera de resultados. El contrato incluye `phase`, `elapsed_seconds` y
  `phase_elapsed_seconds`; los clientes antiguos siguen usando `state`.
- El acceso utiliza el autocompletado existente y Enter. Si falta, puede emplear
  la credencial cifrada que ya tenía el ASUS, sin copiarla al repositorio ni al
  móvil. Reconoce enlaces y botones de acceso, selector de cuenta y contraseña.
- La recuperación del navegador tiene un límite de 75 segundos; la preparación
  completa, 150. Verificación adicional, consentimiento, rechazo o contraseña
  inválida producen una explicación concreta en la app.
- Antes de un nuevo envío se comprueba el editor. Una página incompleta se
  recarga si no contiene un borrador. Como máximo cada cinco minutos se refresca
  también una página aparentemente lista para detectar sesiones caducadas.
  La recuperación no recarga una generación en seguimiento.
- El temporizador usa el mismo acceso y un bloqueo entre procesos. Reintenta un
  acceso atascado tras su período de gracia; desaparece la espera anterior de
  seis horas. Los rechazos que requieren intervención no envían contraseñas en bucle.
- La app presenta la fase, tiempo transcurrido, errores persistentes y acceso a
  las opciones del trabajo. Publica cada archivo copiado durante la descarga.
  Los fallos transitorios anteriores al clic esperan 15 segundos en vez de 90;
  un borrador manual o varios proyectos abiertos requieren resolución explícita.
- Descargar contempla tanto el submenú de tamaño original como la descarga
  directa de las imágenes que no ofrecen resoluciones. No selecciona mejoras.

## Interrupciones y duplicados

El mismo `client_request_id` recupera el pedido existente, incluso si se perdió
la respuesta HTTP. Otro pedido recibe `flow_busy` mientras el anterior está
activo; Android lo mantiene en cola. El comienzo del clic se registra antes de
operarlo. Un timeout posterior queda como `uncertain` y no se reenvía.

Al reiniciar el puente, una preparación sin clic queda como no enviada y una
con clic iniciado queda incierta. Los prompts siguen sin guardarse en el journal
del puente. El móvil conserva su propio historial.

Una generación que tarda más de cinco minutos en imagen o quince en vídeo
expone `generation_delayed`; sigue siendo consultable y no se vuelve a generar.
El límite configurable de seguimiento de Nexus sigue siendo aplicable.

## Validación y despliegue

- AXELPC: compilación AgentPreview y 39 pruebas JVM de Flow/Studio superadas.
  Comando: `:app:testAgentPreviewUnitTest --tests 'com.vidadomu.nexus.provider.flow.*'
  --tests 'com.vidadomu.nexus.ui.flow.*' :app:assembleAgentPreview --offline
  --no-daemon --max-workers=2 --no-parallel`.
- 61 pruebas del puente, cinco de navegador para acceso/recuperación y dos de
  navegador para descarga superadas: 68 casos únicos, sin generaciones nuevas.
  Incluyen acceso con dos Enter, respaldo cifrado simulado, verificación adicional,
  preservación de borradores, timeout posterior al clic, reinicio e idempotencia.
- ASUS: recuperación real de una página sin editor, con resultado `ready` en
  18,47 segundos. Descarga real de un archivo existente de 76.694 bytes:
  SHA-256 `f91de2ed70129ec0fad761ec55957aa27e85d7f7b3b7a24f932be32c50c8ef74`.
- Se reemplazaron únicamente las funciones revisadas de `recovery.py` y los
  módulos correspondientes, conservando los routers administrativos y de
  importación del runtime. Copias previas privadas en el ASUS con sufijo
  `.before-flow-autonomy-20261022`; no se reinició Chromium ni se borraron sesiones.
- S25 Ultra físico `SM-S938B`: actualización instalada sobre vc102, con certificado
  SHA-256 `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`.
  Los 547 identificadores de historial anteriores siguen presentes. Los hashes
  de configuración del asistente, cerebros y preferencias no sensibles se
  conservaron. El almacén seguro está disponible y Flow sigue vinculado.
  El XML cifrado completo varió tras el arranque: también contiene saldos y
  cachés, por lo que no se afirma igualdad binaria de ese archivo.
- Diagnóstico ejecutado dentro de la APK instalada: `connected=true`,
  `ready=true`, `promptLimit=4000`.
- APK local: `outputs/flow-autonomy-20261022/Nexus-Agent-1.9.69-flow-autonomy.apk`.
  SHA-256 `2b1adfebfcb2b30e42d18e7e1683933d49985ac2ace76e47e2f83fb32f989895`.

El acceso con sesión caducada se comprobó con páginas de prueba; no se cerró
deliberadamente la sesión real de Google. No se inició una generación nueva para
validar el recorrido facturable completo. La nueva interfaz se compiló, pero
no se verificó visualmente en el móvil durante una generación real.
