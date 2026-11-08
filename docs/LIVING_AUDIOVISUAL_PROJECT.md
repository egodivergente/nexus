# Proyecto audiovisual vivo

Base de entrega: Nexus Agent vc189, fuente `b3b504f7efc7`, comprobada en el S25 SM-S938B. La evolución vc190 parte de `feat/flow-live-20261108`, que incluye esa versión y la corrección de descargas de Flow.

## Entrada y datos

Agente → Proyecto → **Guion y storyboard**. Las vistas Guion, Storyboard y Material leen el mismo `AssistantRuntime.assets` (`files/assistant/index.json`). No hay una base de guion paralela ni una nueva pestaña principal. La importación de TXT/Markdown o texto pegado conserva el texto en bloques editables sin exigir sintaxis de guion.

Los proyectos antiguos abren con los valores opcionales por defecto. Al abrir el espacio se adoptan una sola vez sus asociaciones antiguas; quitar una asociación no la recrea al volver a abrir. No se duplican ni borran archivos físicos. El fichero y sus usos tienen identificadores distintos. Los vínculos guardan el papel, la selección y la procedencia en cada contexto.

Escenas, bloques y planos conservan sus identificadores al renombrar y reordenar. Personajes, localizaciones, vestuario y restricciones se pueden completar progresivamente. Las referencias efectivas se resuelven por alcance y personaje; el editor muestra su procedencia y el comportamiento complementar/sustituir. Las indicaciones se pueden consultar, editar y desactivar.

## Edición y agentes

El editor guarda borradores privados por entidad junto con su revisión base. Guardar valida esa revisión bajo el mismo mutex que las herramientas. Una edición concurrente produce un conflicto local y conserva el borrador. Las mutaciones tienen un `operation_id` estable y un recibo persistente; reintentarlas no las duplica. El deshacer guarda diferencias por entidad y conserva cambios independientes posteriores.

Las herramientas `read_story_project`, `read_story_fragment`, `edit_story_project`, `propose_story_changes`, `apply_story_proposal`, `search_story_material`, `story_tasks` y `prepare_story_generation` están registradas en `NexusToolkit`. Codex y Antigravity usan el puente y la autenticación existentes; no se añade otro servidor. El alcance explícito queda fijado al comenzar el turno y el contenido se relee después de los cambios. Las propuestas persisten y se pueden aplicar completas o por partes coherentes, verificando sus revisiones.

El chat muestra la selección, el alcance y el modo, y permite quitar ese contexto. El protocolo de los turnos audiovisuales prohíbe imágenes nativas del cerebro y canaliza la generación por las herramientas de Nexus. Esto no constituye una nueva restricción del ejecutable CLI remoto.

## Generación y Flow

Desde un plano, **ArtBox** abre el formulario existente con su acción y referencias preparadas; hay que revisar modelo, medios admitidos, parámetros y precio. El origen inmutable se persiste con el borrador, las comparaciones, el trabajo y `GenerationRecord`. Los metadatos `__story_context` y `__story_inputs` son locales y nunca se añaden al payload enviado al proveedor. Se conservan las referencias realmente adjuntas, el prompt, los parámetros, el modelo y los identificadores disponibles.

El resultado se asocia por ese origen, sin deducirlo de la última pantalla abierta. Los cambios relevantes marcan material para revisar; reordenar o corregir una errata con invalidación desactivada conserva su vigencia. Si se elimina un plano durante el trabajo, el resultado queda recuperable con la procedencia anterior. No se regenera automáticamente.

Flow sigue siendo la sesión remota de Edge en AXELPC, mostrada mediante fotogramas y controles. La barra puede preparar el contexto del plano para revisarlo antes de enviarlo. Las descargas que no tengan una relación verificable se vinculan mediante una acción manual; no se atribuyen por la selección actual.

## Cola y límites de ejecución

La cola y su política se guardan en el índice. `StoryPreparationWorker` usa WorkManager para una pasada local finita cuando se habilita la preparación: índice, referencias, huecos y contexto, sin llamadas a modelos ni proveedores. Pausar se comprueba de nuevo al ejecutar. Los encargos creativos que requieren un cerebro quedan como pendientes del agente; una tarea persistida no implica que haya una inferencia ejecutándose.

Los turnos de agentes reutilizan el servicio en primer plano actual. Tras la muerte del proceso, el trabajo local interrumpido se marca pendiente de reanudación; no se promete un agente remoto continuo. Las generaciones remotas reutilizan `GenerationPollingWorker` y los recibos de envío existentes: un timeout ambiguo no vuelve a enviar una operación facturable.

Las autorizaciones narrativas no emiten tickets de gasto. Se conserva la confirmación de generación y los límites existentes. La implementación y las pruebas no requieren generaciones de pago ni llamadas de prueba a un LLM externo.

## Verificación

Pruebas pertinentes: `StoryWorkspaceTest`, `StoryAgentToolsTest`, `StoryDraftOriginTest`, los guardianes de gasto y las regresiones de material, proyecto y geometría de Flow. La compilación y las pruebas Android se ejecutan en AWS CodeBuild. La verificación física y el artefacto de entrega se registran en `CONTINUAR.md`; no se deducen de la compilación.
