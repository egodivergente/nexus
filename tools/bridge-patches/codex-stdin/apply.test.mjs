import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { transform, MARK } from "./apply.mjs";

const APPLY = path.join(path.dirname(fileURLToPath(import.meta.url)), "apply.mjs");

// Forma del repo (axel-native, bridge/server.ts).
const REPO_SHAPE = `
      try {
        const tmpOutput = \`/tmp/codex_out_\${jobId}.txt\`;
        const codexArgs = [
          "exec",
          "--dangerously-bypass-approvals-and-sandbox",
          "--skip-git-repo-check",
          ...(requestedModel && requestedModel !== "codex-default" ? ["--model", requestedModel] : []),
          "-o", tmpOutput,
          multiTurnPrompt,
        ];
        const child = spawn("/usr/local/bin/codex", codexArgs, { stdio: ["ignore", "pipe", "pipe"] });
        activeChildren.set(jobId, child);
      } catch (err: any) {}
`;

// Forma de la copia desplegada: opciones extra (-c …), env propio y otro nombre de variable.
const DEPLOYED_SHAPE = `
        const codexArgs = [
            "exec",
            "--skip-git-repo-check",
            "-c", \`model_reasoning_effort="\${effort}"\`,
            ...(requestedModel ? ["--model", requestedModel] : []),
            "-o", tmpOutput,
            multiTurnPrompt,
        ];
        const proc = spawn(CODEX_BIN, codexArgs, { env: { ...process.env, HOME: "/root" }, stdio: ["ignore", "pipe", "pipe"], detached: false });
`;

test("forma del repo: el texto pasa a stdin y el argumento es «-»", () => {
  const r = transform(REPO_SHAPE);
  assert.equal(r.ok, true);
  assert.match(r.text, /"-", \/\/ NEXUS-CODEX-STDIN/);
  assert.doesNotMatch(r.text, /^\s*multiTurnPrompt,$/m);
  assert.match(r.text, /stdio: \["pipe", "pipe", "pipe"\]/);
  assert.match(r.text, /child\.stdin\?\.on\("error", \(\) => \{\}\);/);
  assert.match(r.text, /child\.stdin\?\.end\(multiTurnPrompt\);/);
  assert.ok(r.text.indexOf("stdin?.end") > r.text.indexOf("const child = spawn("), "se escribe después de crear el proceso");
  assert.equal(r.text.split("\n").length - REPO_SHAPE.split("\n").length, 2);
});

test("copia desplegada: conserva -c, env y el nombre de la variable", () => {
  const r = transform(DEPLOYED_SHAPE);
  assert.equal(r.ok, true);
  assert.match(r.text, /"-c", `model_reasoning_effort/);
  assert.match(r.text, /env: \{ \.\.\.process\.env, HOME: "\/root" \}, stdio: \["pipe", "pipe", "pipe"\], detached: false/);
  assert.match(r.text, /proc\.stdin\?\.end\(multiTurnPrompt\);/);
  assert.doesNotMatch(r.text, /child\.stdin/);
});

test("es idempotente", () => {
  const once = transform(REPO_SHAPE);
  const twice = transform(once.text);
  assert.equal(twice.ok, true);
  assert.equal(twice.already, true);
  assert.equal(twice.text, once.text);
  assert.ok(once.text.includes(MARK));
});

test("rechaza formas inesperadas sin adivinar", () => {
  const cases = {
    "multiTurnPrompt no es el último": REPO_SHAPE.replace('"-o", tmpOutput,\n          multiTurnPrompt,', 'multiTurnPrompt,\n          "-o", tmpOutput,'),
    "sin spawn de codexArgs": REPO_SHAPE.replace("spawn(", "lanzar("),
    "stdin ya no es ignore": REPO_SHAPE.replace('["ignore", "pipe", "pipe"]', '["pipe", "pipe", "pipe"]'),
    "dos codexArgs": REPO_SHAPE + REPO_SHAPE,
    "fichero ajeno": "export const x = 1;\n",
  };
  for (const [name, source] of Object.entries(cases)) {
    const r = transform(source);
    assert.equal(r.ok, false, name);
    assert.ok(r.reason, name);
  }
});

test("línea de comandos: copia de seguridad, permisos, dry-run y segunda ejecución sin cambios", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "codex-stdin-"));
  try {
    const file = path.join(dir, "server.ts");
    fs.writeFileSync(file, REPO_SHAPE, { mode: 0o640 });
    const run = (...args) => spawnSync(process.execPath, [APPLY, file, ...args], { encoding: "utf8" });

    let r = run("--dry-run");
    assert.equal(r.status, 0, r.stderr);
    assert.equal(fs.readFileSync(file, "utf8"), REPO_SHAPE, "el dry-run no escribe");
    assert.equal(fs.readdirSync(dir).length, 1);

    r = run();
    assert.equal(r.status, 0, r.stderr);
    const backups = fs.readdirSync(dir).filter((n) => n.startsWith("server.ts.bak-codex-stdin-"));
    assert.equal(backups.length, 1);
    assert.equal(fs.readFileSync(path.join(dir, backups[0]), "utf8"), REPO_SHAPE, "la copia es el original");
    assert.equal(fs.statSync(file).mode & 0o777, 0o640, "conserva los permisos");
    assert.ok(fs.readFileSync(file, "utf8").includes(MARK));
    assert.equal(fs.readdirSync(dir).filter((n) => n.includes(".tmp-")).length, 0, "no deja temporales");

    const patched = fs.readFileSync(file, "utf8");
    r = run();
    assert.equal(r.status, 0, r.stderr);
    assert.equal(fs.readFileSync(file, "utf8"), patched);
    assert.equal(fs.readdirSync(dir).filter((n) => n.startsWith("server.ts.bak-")).length, 1, "no duplica copias");

    const alien = path.join(dir, "otro.ts");
    fs.writeFileSync(alien, "nada que ver\n");
    r = spawnSync(process.execPath, [APPLY, alien], { encoding: "utf8" });
    assert.equal(r.status, 2);
    assert.equal(fs.readFileSync(alien, "utf8"), "nada que ver\n");
    assert.deepEqual(fs.readdirSync(dir).filter((n) => n.startsWith("otro.ts.")), []);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("el texto de más de 128 KiB llega íntegro por stdin a un proceso real", () => {
  // Lo que cambia el parche, en pequeño: mismo texto, un argumento (falla) frente a stdin (llega entero).
  const text = ("a".repeat(79) + "\n").repeat(2200); // 176 000 B > 131 072
  const reader = "let n=0;process.stdin.on('data',c=>n+=c.length).on('end',()=>process.stdout.write(String(n)))";
  const viaArgv = spawnSync(process.execPath, ["-e", "process.stdout.write('ok')", text]);
  assert.equal(viaArgv.error?.code, "E2BIG");
  const viaStdin = spawnSync(process.execPath, ["-e", reader], { input: text });
  assert.equal(viaStdin.stdout.toString(), String(Buffer.byteLength(text)));
});
