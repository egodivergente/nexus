#!/usr/bin/env node
// Pasa el texto de la conversación a Codex por stdin en el puente de AXEL (bridge/server.ts).
//
// Causa: server.ts lanzaba `codex exec … <multiTurnPrompt>` con el texto entero como UN argumento y `stdin: "ignore"`.
// Linux limita cada argumento a 128 KiB (MAX_ARG_STRLEN = 131072 bytes), y `spawn` falla con `spawn E2BIG` en cuanto
// el historial supera ese tamaño. La interfaz real de la CLI (`codex exec --help`, v0.167.0) dice: «PROMPT: si no se da
// como argumento (o si se usa `-`), las instrucciones se leen de stdin». Aquí se usa `-` y se escribe el texto en stdin.
//
// Uso:   node apply.mjs <ruta/a/server.ts> [--dry-run]
// Salida: 0 aplicado o ya aplicado · 2 el fichero no tiene la forma esperada (no se modifica nada) · 1 error de uso.
//
// Es estricto a propósito: solo toca el fichero si encuentra EXACTAMENTE un `codexArgs` cuyo último elemento es
// `multiTurnPrompt` y EXACTAMENTE un `spawn(<codex>, codexArgs, { … stdio: ["ignore","pipe","pipe"] })`. Admite la copia
// desplegada en el móvil (con `-c model_reasoning_effort=…` y otras opciones) porque no depende del resto del array.
import fs from "node:fs";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

export const MARK = "NEXUS-CODEX-STDIN";

const ARGS_RE = /(const|let)\s+codexArgs\s*=\s*\[([\s\S]*?)\n([ \t]*)\];/g;
// Las opciones de spawn pueden llevar un objeto anidado (p. ej. `env: { ...process.env, HOME: "/root" }`): se admite un nivel.
const OPT = String.raw`(?:[^{}]|\{[^{}]*\})*?`;
const SPAWN_RE = new RegExp(String.raw`(const|let)\s+(\w+)\s*=\s*spawn\(\s*([^,]+?)\s*,\s*codexArgs\s*,\s*\{(${OPT})stdio:\s*\[\s*"ignore"\s*,\s*"pipe"\s*,\s*"pipe"\s*\](${OPT})\}\s*\);`, "g");

// Forma B (copia desplegada desde vc182): `const runCodex = async (codexArgs: string[]) => { const child = spawn(…, codexArgs,
// {…stdio ["ignore",…]}) … }`, llamada con el texto como último argumento tanto en `exec … <texto>` como en
// `exec … resume <id> <texto>`. Ambas formas de la CLI aceptan `-` para leerlo de stdin (`codex exec --help` y
// `codex exec resume --help`, v0.167.0).
const PARAM_RE = /async\s*\(\s*codexArgs\s*:\s*string\[\]\s*\)\s*=>\s*\{/g;

function transformParam(source) {
  const params = [...source.matchAll(PARAM_RE)];
  if (params.length !== 1) return { ok: false, reason: `se esperaba una única función (codexArgs: string[]) y hay ${params.length}` };
  const spawnMatches = [...source.matchAll(SPAWN_RE)];
  if (spawnMatches.length !== 1) return { ok: false, reason: `se esperaba un único spawn(…, codexArgs, {… stdio ["ignore","pipe","pipe"]}) y hay ${spawnMatches.length}` };
  const m = spawnMatches[0];
  if (m.index < params[0].index) return { ok: false, reason: "el spawn no está dentro de la función que recibe codexArgs" };
  const [whole, decl, name, bin, before, after] = m;
  const lineStart = source.lastIndexOf("\n", m.index) + 1;
  const pad = /^[ \t]*$/.test(source.slice(lineStart, m.index)) ? source.slice(lineStart, m.index) : "";
  const replacement =
    `// ${MARK}: el texto (último argumento) va por stdin con «-»; un argumento de más de 128 KiB da E2BIG.\n` +
    `${pad}${decl} ${name} = spawn(${bin}, [...codexArgs.slice(0, -1), "-"], {${before}stdio: ["pipe", "pipe", "pipe"]${after}});\n` +
    `${pad}${name}.stdin?.on("error", () => {}); // EPIPE si Codex termina antes de leer todo el texto\n` +
    `${pad}${name}.stdin?.end(codexArgs[codexArgs.length - 1] ?? "");`;
  return { ok: true, text: source.slice(0, m.index) + replacement + source.slice(m.index + whole.length), shape: "B" };
}

/** Devuelve { ok, text, reason }. No toca el disco. */
export function transform(source) {
  if (source.includes(MARK)) return { ok: true, text: source, already: true };
  // Sin array literal `codexArgs = ["exec", …]` pero con una función que lo recibe: forma B.
  if (![...source.matchAll(ARGS_RE)].some((m) => /"exec"/.test(m[2])) && PARAM_RE.test(source)) {
    PARAM_RE.lastIndex = 0;
    return transformParam(source);
  }
  PARAM_RE.lastIndex = 0;

  const argsMatches = [...source.matchAll(ARGS_RE)].filter((m) => /"exec"/.test(m[2]));
  if (argsMatches.length !== 1) return { ok: false, reason: `se esperaba un único «codexArgs» con "exec" y hay ${argsMatches.length}` };
  const argsMatch = argsMatches[0];
  const body = argsMatch[2];
  const lastLine = body.trimEnd().split("\n").pop() ?? "";
  if (lastLine.trim() !== "multiTurnPrompt,") {
    return { ok: false, reason: "el último elemento de codexArgs no es «multiTurnPrompt,»; no se adivina otro formato" };
  }
  if ((body.match(/\bmultiTurnPrompt\b/g) ?? []).length !== 1) return { ok: false, reason: "«multiTurnPrompt» aparece más de una vez dentro de codexArgs" };

  const spawnMatches = [...source.matchAll(SPAWN_RE)];
  if (spawnMatches.length !== 1) return { ok: false, reason: `se esperaba un único spawn(…, codexArgs, {… stdio ["ignore","pipe","pipe"]}) y hay ${spawnMatches.length}` };
  const spawnMatch = spawnMatches[0];
  if (spawnMatch.index < argsMatch.index) return { ok: false, reason: "el spawn aparece antes que codexArgs" };

  const indent = lastLine.match(/^[ \t]*/)[0];
  const newBody = body.replace(lastLine, `${indent}"-", // ${MARK}: lee el texto de stdin; un argumento de más de 128 KiB da E2BIG`);
  const newArgs = `${argsMatch[1]} codexArgs = [${newBody}\n${argsMatch[3]}];`;

  const [whole, decl, name, bin, before, after] = spawnMatch;
  const spawnIndent = source.slice(source.lastIndexOf("\n", spawnMatch.index) + 1, spawnMatch.index);
  const pad = /^[ \t]*$/.test(spawnIndent) ? spawnIndent : "";
  const newSpawn =
    `${decl} ${name} = spawn(${bin}, codexArgs, {${before}stdio: ["pipe", "pipe", "pipe"]${after}});\n` +
    `${pad}${name}.stdin?.on("error", () => {}); // EPIPE si Codex termina antes de leer todo el texto\n` +
    `${pad}${name}.stdin?.end(multiTurnPrompt); // ${MARK}`;

  // Se sustituye de atrás hacia delante para que los índices no se muevan.
  let text = source;
  text = text.slice(0, spawnMatch.index) + newSpawn + text.slice(spawnMatch.index + whole.length);
  text = text.slice(0, argsMatch.index) + newArgs + text.slice(argsMatch.index + argsMatch[0].length);
  return { ok: true, text };
}

const sha = (s) => crypto.createHash("sha256").update(s).digest("hex").slice(0, 12);

function main(argv) {
  const dry = argv.includes("--dry-run");
  const files = argv.filter((a) => !a.startsWith("--"));
  if (files.length !== 1) {
    console.error("Uso: node apply.mjs <ruta/a/server.ts> [--dry-run]");
    return 1;
  }
  const file = files[0];
  let source;
  try { source = fs.readFileSync(file, "utf8"); } catch (e) { console.error(`No se puede leer ${file}: ${e.message}`); return 1; }
  const result = transform(source);
  if (!result.ok) { console.error(`NO SE MODIFICÓ ${file}: ${result.reason}.`); return 2; }
  if (result.already) { console.log(`${file}: ya tenía el parche (${MARK}); sin cambios.`); return 0; }
  if (dry) {
    const a = source.split("\n"), b = result.text.split("\n");
    console.log(`--dry-run: ${file} cambiaría de ${a.length} a ${b.length} líneas (sha ${sha(source)} → ${sha(result.text)}).`);
    return 0;
  }
  const stamp = new Date().toISOString().replace(/\D/g, "").slice(0, 14);
  const backup = `${file}.bak-codex-stdin-${stamp}`;
  const mode = fs.statSync(file).mode & 0o777;
  fs.copyFileSync(file, backup);
  fs.chmodSync(backup, mode);
  const tmp = `${file}.tmp-${process.pid}`;
  fs.writeFileSync(tmp, result.text, { mode });
  fs.renameSync(tmp, file);
  console.log(`Parche aplicado a ${file} (sha ${sha(source)} → ${sha(result.text)}). Copia de seguridad: ${backup}`);
  console.log("Reinicia el puente para que surta efecto.");
  return 0;
}

if (process.argv[1] && fs.realpathSync(process.argv[1]) === fs.realpathSync(fileURLToPath(import.meta.url))) process.exit(main(process.argv.slice(2)));
