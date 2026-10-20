#!/usr/bin/env bash
# Valida el parseo de argumentos de flow.sh sin red (FLOW_DRY_RUN=1).
set -u
S="$(cd "$(dirname "$0")" && pwd)/flow.sh"
export FLOW_DRY_RUN=1 NEXUS_FLOW_OUT=/nonexistent/out
pass=0; fail=0
ok()  { if out="$(bash "$S" "$@" 2>&1)"; then pass=$((pass+1)); else fail=$((fail+1)); echo "FAIL ok: $* -> $out"; fi; }
bad() { if out="$(bash "$S" "$@" 2>&1)"; then fail=$((fail+1)); echo "FAIL bad: $* -> $out"; else pass=$((pass+1)); fi; }
has() { pat="$1"; shift; out="$(bash "$S" "$@" 2>&1)"; case "$out" in *"$pat"*) pass=$((pass+1));; *) fail=$((fail+1)); echo "FAIL has '$pat': $* -> $out";; esac; }

has "DRY GET /status" status
has '"expected_output":"image"' submit-image --prompt "gato" --n 4
has '"runs":4' submit-image --prompt "gato" --n 4
has '"reference_ids":["ref_a","ref_b"]' submit-image --prompt x --ref ref_a --ref ref_b
has 'Formato 9:16.' submit-image --prompt x --aspect 9:16
has '"confirmed":true' submit-video --prompt x
has '"mode":"Texto"' submit-video --prompt x
has '"mode":"Referencias"' submit-video --prompt x --ref r1
has 'DRY GET /jobs/job_1' job job_1
has 'DRY WAIT /jobs/j1 timeout=60 interval=10' wait j1 --timeout 60 --interval 3
has 'DRY GET /jobs/j1/downloads/2' download j1 --index 2
has 'DRY DOWNLOAD-ALL /jobs/j1' download j1 --all
bad submit-image
bad submit-image --prompt x --n 0
bad submit-image --prompt x --n 11
bad submit-image --prompt x --n abc
bad submit-video --prompt x --aspect 1:1
bad submit-image --prompt x --ref 'a/b'
bad submit-image --prompt x --bogus
bad job '../etc'
bad job
bad upload /nonexistent.png
bad download j1 --index x
bad nope
ok --help
t="$(mktemp -d)"; printf 'x' > "$t/a.gif"; bad upload "$t/a.gif"; printf 'x' > "$t/a.PNG"
has 'Content-Type: image/png' upload "$t/a.PNG"; rm -rf "$t"
if grep -q "$(printf '\r')" "$S"; then fail=$((fail+1)); echo "FAIL: flow.sh tiene CRLF"; else pass=$((pass+1)); fi
echo "PASS=$pass FAIL=$fail"; [ "$fail" -eq 0 ]
