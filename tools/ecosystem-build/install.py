"""Install this reviewed router and global Codex skill without changing PATH/services."""
import datetime
import hashlib
import json
import os
import shutil
from pathlib import Path

ROOT=Path(__file__).resolve().parent
CODEX=Path(os.environ.get("CODEX_HOME",str(Path.home()/".codex")))
target=CODEX/"tools"/"ecosystem-build"
target.mkdir(parents=True,exist_ok=True)
for p in ROOT.rglob("*"):
    if not p.is_file() or "__pycache__" in p.parts or p.name=="nodes.local.json": continue
    rel=p.relative_to(ROOT); dest=target/rel; dest.parent.mkdir(parents=True,exist_ok=True)
    if p.resolve()!=dest.resolve(): shutil.copy2(p,dest)
manifest={p.relative_to(target).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
          for p in target.rglob("*") if p.is_file() and "__pycache__" not in p.parts and p.name!="installation.json"}
(target/"installation.json").write_text(json.dumps({"installed_from":str(ROOT),"files":manifest},indent=2),encoding="utf-8")
skill=CODEX/"skills"/"ecosystem-build"; (skill/"agents").mkdir(parents=True,exist_ok=True)
(skill/"SKILL.md").write_text('''---
name: ecosystem-build
description: Evalúa el estado real de ASUS, Pixel 8 Pro, EliteBook y S25 antes de compilar APK, EXE u otros artefactos; selecciona un ejecutor compatible y protege los dispositivos en uso.
---

Usar en tareas de compilación y empaquetado del usuario. La petición de compilar autoriza la selección y ejecución rutinaria en sus equipos disponibles. Respetar decisiones explícitas del usuario.

Herramienta instalada: `C:/Users/User/.codex/tools/ecosystem-build/router.py`.
Configuración: `nodes.json` junto a la herramienta. Detalles y recetas: `README.md` y `recipes/` en esa misma carpeta.

1. Revisar la receta del proyecto o crear una para sus comandos reales: sistemas/arquitecturas compatibles, herramientas, SDK/JDK, RAM, fuentes incluidas y artefactos esperados. Un EXE no exige Windows si existe un compilador cruzado adecuado, pero debe demostrarse. Termux instalado o ADB conectado no demuestran capacidad de compilar.
2. Ejecutar `python <router.py> plan --recipe <receta.json> --source <proyecto>`. Son mediciones actuales, no valores históricos ni una promesa de que un nodo será siempre el más rápido. Si SSH falla, consultar Desktop Commander `list_devices` y usar `probe-command --node asus` a través de su `start_process`; conservar la misma identidad y protocolo de trabajo.
3. Ejecutar `submit` con esa receta y el código actual. Si no hay nodo apto, mantener la tarea pendiente y reevaluar con `--wait-seconds 60`; no ignorar los bloqueos ni iniciar otra compilación. Si el estado de un envío es ambiguo, consultar su job_id; nunca repetirlo a ciegas. La herramienta vuelve a comprobar recursos antes de arrancar.
4. Seguir `status <job_id> --tail 20` y terminar con `collect <job_id> --dest <directorio>`. Comprobar éxito, manifiestos y SHA-256. Informar del nodo elegido, resultado y limitaciones sin extenderse. Instalar o probar el producto únicamente si forma parte del encargo.

ASUS y Pixel son dedicados y tienen preferencia cuando son compatibles y están libres. EliteBook: ventanas ocultas, Job Object con CPU al 15 %, memoria limitada y prioridad baja. S25: solo ejecutor de fondo verificado, pantalla apagada, temperatura y batería adecuadas. No abrir Terminal/Termux ni inyectar teclas para cada compilación automática. El Pixel usa Termux nativo por SSH privado sobre ADB; su Linux nativo está actualizado y conservado, pero fuera de la ruta automática por ANR de Terminal. Consultar nodes.local.json y README.md. La configuración inicial del Pixel dedicado forma parte del encargo de montar el sistema, comprobando que está libre y validando después el canal en segundo plano. No despertar el S25, arrancar emuladores ni cambiar ADB a tcpip.

Los móviles pueden tener `executor: null`: significa pendiente de verificar una ruta en segundo plano, no falta de potencia ni ausencia de Termux. No rellenar direcciones, usuario SSH ni claves por suposición. Establecer el ejecutor únicamente con acceso real al teléfono y confirmar ejecución, límites, identidad y recuperación de artefactos antes de marcarlo listo.

No parar servicios existentes, cambiar fuentes ajenas, transferir claves de firma o ejecutar generaciones de pago. Firmar con la clave existente en su equipo custodio y comprobar su certificado cuando el encargo requiera una actualización instalable.
''',encoding="utf-8")
(skill/"agents"/"openai.yaml").write_text('''interface:
  display_name: "Compilación del ecosistema"
  short_description: "Elige equipo y compila sin saturar los dispositivos"
  default_prompt: "Usa $ecosystem-build para evaluar mis dispositivos y compilar este proyecto en el equipo adecuado."
policy:
  allow_implicit_invocation: true
''',encoding="utf-8")
global_agents=CODEX/"AGENTS.md"
existing=global_agents.read_text(encoding="utf-8-sig") if global_agents.exists() else ""
heading="## Compilaciones y pruebas: liberar el EliteBook"
new='''## Compilaciones y pruebas: selector del ecosistema

Petición del usuario del 2026-10-06: antes de cada compilación o empaquetado de APK, EXE u otro artefacto, evaluar el estado actual del ecosistema y ejecutar en el dispositivo compatible más conveniente, protegiendo su uso interactivo. Aplicar la habilidad `ecosystem-build` en `C:/Users/User/.codex/skills/ecosystem-build/SKILL.md` y su herramienta `C:/Users/User/.codex/tools/ecosystem-build/router.py`.

- Ejecutar la evaluación real con una receta del proyecto y remitir el trabajo por el selector. ASUS y Pixel 8 Pro son dedicados; EliteBook y S25 suelen estar en uso. La compatibilidad, disponibilidad, memoria libre, carga, bloqueos y temperatura prevalecen sobre una preferencia fija de dispositivo.
- Usar SSH/Tailscale y ADB existentes; Desktop Commander como ruta alternativa cuando esté disponible. Verificar identidad y puertos actuales. No confundir conexión ADB con permisos para ejecutar dentro de Termux.
- EliteBook: ejecución oculta, prioridad baja, CPU al 15 %, memoria y paralelismo limitados. S25: solo ejecución en segundo plano verificada, sin abrir aplicaciones ni inyectar teclas; abstenerse cuando esté en uso, caliente o sin recursos. Si no hay ejecutor apto, reevaluar y dejar pendiente sin forzar el equipo activo.
- Una compilación por nodo; conservar servicios y trabajos de otras tareas. Copiar el código actual a una carpeta aislada con manifiesto SHA-256; excluir cachés, outputs, credenciales y local.properties. Recuperar y verificar artefactos y logs. Ante un envío ambiguo, consultar el trabajo y no duplicarlo.
- Para pruebas Android, preferir el Pixel físico disponible, también por ADB del ASUS. No arrancar emuladores si el Pixel cubre las pruebas. La autorización para compilar permite la selección rutinaria de ejecutor; no implica abrir apps en móviles en uso, instalar el resultado, borrar datos o realizar operaciones de pago.
'''
if heading in existing:
    before,after=existing.split(heading,1)
    # Preserve any subsequent unrelated top-level section.
    import re
    next_section=re.search(r"\n## ",after)
    updated=before+new+(after[next_section.start():] if next_section else "")
elif "## Compilaciones y pruebas: selector del ecosistema" in existing:
    updated=existing
else:
    updated=existing.rstrip()+"\n\n"+new
if updated!=existing:
    if global_agents.exists():
        backup=global_agents.with_name("AGENTS.md.before-ecosystem-"+datetime.datetime.now().strftime("%Y%m%d-%H%M%S")+".bak")
        shutil.copy2(global_agents,backup)
    global_agents.write_text(updated,encoding="utf-8")
bindir=CODEX/"bin"; bindir.mkdir(exist_ok=True)
(bindir/"ecobuild.cmd").write_text('@echo off\r\n"'+os.sys.executable+'" "'+str(target/"router.py")+'" %*\r\n',encoding="utf-8")
print(json.dumps({"installed":str(target),"skill":str(skill),"global_instructions":str(global_agents),"verified_files":len(manifest)},indent=2))
