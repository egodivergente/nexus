#!/usr/bin/env python3
"""Comprueba si un puente AXEL pasa el texto a Codex por stdin (sin `spawn E2BIG`).

Uso: python3 verify-bridge.py [URL_DEL_PUENTE]        (por defecto http://127.0.0.1:8788)

Envía cinco peticiones inocuas («Responde solo con la palabra OK») con conversaciones de diagnóstico propias, de modo que
no toca ningún chat real, y NO imprime contenido: solo tamaños y resultado. Cada petición que llega a Codex consume una
vuelta de la cuota del plan (unas decenas de miles de tokens en total).

Con el puente SIN parchear: los casos 3, 4 y 5 fallan con `spawn E2BIG` (el texto va como argumento: máx. 128 KiB).
Con el puente parcheado (apply.mjs): los cinco terminan `completed`.
Salida: 0 si todo pasa · 1 si algún caso falla.
"""
import json, re, sys, time, urllib.request, uuid

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8788").rstrip("/")
ASK = "Responde solo con la palabra OK."


def fill(n):
    return ("a" * 79 + "\n") * (n // 80)


def turn(i):
    return {"role": "user" if i % 2 == 0 else "assistant", "content": fill(26_640)}


def ask(prompt, messages, timeout=180):
    body = json.dumps({
        "jobId": "nexus-codex-verify-" + uuid.uuid4().hex[:10], "conversationId": "verify-e2big-" + uuid.uuid4().hex[:8],
        "provider": "codex", "model": "gpt-6.1-sol", "prompt": prompt, "messages": messages,
    }).encode()
    req = urllib.request.Request(BASE + "/v1/chat", data=body, headers={"Content-Type": "application/json", "Accept": "text/event-stream"})
    final = error = None
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            for raw in r:
                line = raw.decode("utf-8", "replace").rstrip("\n")
                if not line.startswith("data:"):
                    continue
                try:
                    d = json.loads(line[5:].strip())
                except ValueError:
                    continue
                if d.get("type") == "final":
                    final = str(d.get("status"))
                    if d.get("error") and str(d["error"]) != "None":
                        error = re.sub(r"\s+", " ", str(d["error"]))[:100]
                if time.time() - t0 > timeout:
                    break
    except Exception as ex:  # red, puente caído…
        error = "cliente: " + type(ex).__name__
    return final, error, len(body)


CASES = [
    ("1 control: todo pequeño", ASK, [{"role": "user", "content": ASK}]),
    ("2 prompt de 100 KB", fill(100_000) + "\n" + ASK, [{"role": "user", "content": ASK}]),
    ("3 prompt de 140 KB (> 128 KiB)", fill(140_000) + "\n" + ASK, [{"role": "user", "content": ASK}]),
    ("4 historial de 160 KB en 6 mensajes + «?»", ASK, [turn(i) for i in range(6)] + [{"role": "user", "content": "?"}]),
    ("5 sistema 54 KB + historial de 360 KB + «?»", "?",
     [{"role": "system", "content": fill(54_000)}] + [{"role": "user" if i % 2 == 0 else "assistant", "content": fill(60_000)} for i in range(6)] + [{"role": "user", "content": "?"}]),
]

failed = 0
print(f"Puente: {BASE}")
for name, prompt, messages in CASES:
    final, error, size = ask(prompt, messages)
    ok = final == "completed"
    failed += not ok
    print(f"{'PASA ' if ok else 'FALLA'}  {name:<48} cuerpo={size:>7} B  estado={final}  error={error}")
print("RESULTADO: " + ("el puente acepta textos de más de 128 KiB" if not failed else f"{failed} caso(s) fallan; si el error es `spawn E2BIG`, falta aplicar el parche"))
sys.exit(1 if failed else 0)
