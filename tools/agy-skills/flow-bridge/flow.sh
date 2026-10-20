#!/usr/bin/env bash
# Cliente del puente NEXUS Flow (recovery.py, /api/flow). FLOW_DRY_RUN=1 imprime la petición sin red.
set -eu
CONF="${NEXUS_FLOW_CONF:-$HOME/.config/nexus-flow}"
OUT_ROOT="${NEXUS_FLOW_OUT:-$HOME/storage/downloads/NEXUS_Flow}"
DEFAULT_HOSTS="http://100.72.111.97:8899/api/flow http://100.112.57.2:8899/api/flow"
MAX_RUNS=10

die() { echo "flow.sh: $*" >&2; exit 2; }
dry() { [ "${FLOW_DRY_RUN:-0}" = "1" ]; }
usage() {
  cat <<'U'
Uso: flow.sh <comando> [opciones]
  status
  submit-image --prompt TXT [--n 1-10] [--aspect 16:9|9:16|1:1|4:3|3:4] [--ref ID]...
  submit-video --prompt TXT [--n 1-10] [--aspect 16:9|9:16] [--ref ID]...
  upload FICHERO(.png|.jpg|.jpeg|.webp)
  job JOB_ID
  wait JOB_ID [--timeout SEG] [--interval SEG]
  download JOB_ID [--index N | --all] [--dir DIR]
U
}

hosts() {
  if [ -r "$CONF/hosts" ]; then grep -v '^[[:space:]]*#' "$CONF/hosts" | tr -d '\r' | grep -v '^[[:space:]]*$'
  else printf '%s\n' $DEFAULT_HOSTS; fi
}
token() {
  [ -r "$CONF/token" ] || die "falta $CONF/token (chmod 600)"
  tr -d '\r\n' < "$CONF/token"
}
# El token viaja por stdin como cabecera: nunca aparece en argv ni en la salida.
call() {
  m="$1"; p="$2"; shift 2
  if dry; then printf 'DRY %s %s' "$m" "$p"; for a in "$@"; do printf ' %s' "$a"; done; echo; return 0; fi
  tok="$(token)"
  for h in $(hosts); do
    if printf 'Authorization: Bearer %s\n' "$tok" | curl -fsS --connect-timeout 5 --max-time 120 \
         -H @- -X "$m" "$@" "${h%/}$p"; then return 0; fi
    echo "flow.sh: fallo en $h, probando siguiente" >&2
  done
  die "ningún host del puente respondió"
}
need_jq() { dry || command -v jq >/dev/null || die "instala jq (apt install jq)"; }
valid_id() { printf '%s' "$1" | grep -Eq '^[A-Za-z0-9_-]{1,128}$' || die "id no válido: $1"; }
req_id() { printf 'agy%s%05d' "$(date +%Y%m%d%H%M%S)" "$(( $(od -An -N2 -tu2 /dev/urandom | tr -d ' ') ))"; }
json_str() {
  if command -v jq >/dev/null 2>&1; then jq -Rs .
  else awk 'BEGIN{ORS=""} {gsub(/\\/,"\\\\");gsub(/"/,"\\\"");gsub(/\t/,"\\t"); a[NR]=$0} END{printf "\""; for(i=1;i<=NR;i++){printf "%s%s", (i>1?"\\n":""), a[i]} printf "\""}'; fi
}

submit() {
  kind="$1"; shift; prompt=""; n=1; aspect=""; refs=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --prompt) [ $# -ge 2 ] || die "--prompt sin valor"; prompt="$2"; shift 2;;
      --n) [ $# -ge 2 ] || die "--n sin valor"; n="$2"; shift 2;;
      --aspect) [ $# -ge 2 ] || die "--aspect sin valor"; aspect="$2"; shift 2;;
      --ref) [ $# -ge 2 ] || die "--ref sin valor"; valid_id "$2"; refs="$refs $2"; shift 2;;
      *) die "opción desconocida: $1";;
    esac
  done
  [ -n "$prompt" ] || die "--prompt es obligatorio"
  case "$n" in ''|*[!0-9]*) die "--n debe ser entero";; esac
  { [ "$n" -ge 1 ] && [ "$n" -le $MAX_RUNS ]; } || die "--n entre 1 y $MAX_RUNS"
  if [ -n "$aspect" ]; then
    if [ "$kind" = video ]; then ok='16:9 9:16'; else ok='16:9 9:16 1:1 4:3 3:4'; fi
    case " $ok " in *" $aspect "*) ;; *) die "--aspect no válido para $kind: $aspect";; esac
    # El puente no tiene campo de formato: se pide en el propio prompt.
    prompt="$prompt. Formato $aspect."
  fi
  need_jq
  mode=""; if [ "$kind" = video ]; then if [ -n "$refs" ]; then mode=Referencias; else mode=Texto; fi; fi
  refs_json="$(printf '%s\n' $refs | awk 'NF{printf "%s\"%s\"", (c++?",":""), $0}')"
  body="{\"prompt\":$(printf '%s' "$prompt" | json_str),\"expected_output\":\"$kind\",\"client_request_id\":\"$(req_id)\",\"confirmed\":true,\"reference_ids\":[${refs_json}],\"runs\":$n"
  [ -z "$mode" ] || body="$body,\"mode\":\"$mode\""
  call POST /jobs -H 'Content-Type: application/json' --data-binary "$body}"
}

upload() {
  [ $# -eq 1 ] || die "upload FICHERO"
  f="$1"; [ -f "$f" ] || die "no existe: $f"
  case "$(printf '%s' "$f" | tr 'A-Z' 'a-z')" in
    *.png) mt=image/png;; *.jpg|*.jpeg) mt=image/jpeg;; *.webp) mt=image/webp;;
    *) die "solo PNG, JPEG o WebP";; esac
  call POST /references -H "Content-Type: $mt" --data-binary "@$f"
}

wait_job() {
  [ $# -ge 1 ] || die "wait JOB_ID"; id="$1"; shift; valid_id "$id"; to=1800; iv=20
  while [ $# -gt 0 ]; do case "$1" in
    --timeout) to="${2:-}"; shift 2;; --interval) iv="${2:-}"; shift 2;; *) die "opción desconocida: $1";; esac; done
  case "x$to$iv" in x|*[!0-9x]*) die "timeout/interval enteros";; esac
  [ "$iv" -ge 10 ] || iv=10
  if dry; then echo "DRY WAIT /jobs/$id timeout=$to interval=$iv"; return 0; fi
  need_jq; start=$(date +%s)
  while :; do
    j="$(call GET "/jobs/$id")"; s="$(printf '%s' "$j" | jq -r .state)"
    case "$s" in completed|failed|uncertain) printf '%s\n' "$j"; [ "$s" = completed ]; return;; esac
    [ $(( $(date +%s) - start )) -lt "$to" ] || { printf '%s\n' "$j"; die "timeout esperando $id (estado $s)"; }
    sleep "$iv"
  done
}

download() {
  [ $# -ge 1 ] || die "download JOB_ID"; id="$1"; shift; valid_id "$id"; idx=""; all=0
  dir="$OUT_ROOT/$(date +%Y-%m-%d)"
  while [ $# -gt 0 ]; do case "$1" in
    --index) idx="${2:-}"; shift 2;; --all) all=1; shift;; --dir) dir="${2:-}"; shift 2;; *) die "opción desconocida: $1";; esac; done
  [ -z "$idx" ] || case "$idx" in *[!0-9]*) die "--index entero";; esac
  if [ "$all" = 1 ]; then
    if dry; then echo "DRY DOWNLOAD-ALL /jobs/$id -> $dir"; return 0; fi
    need_jq; cnt="$(call GET "/jobs/$id" | jq -r '.result_count // 0')"; i=0
    while [ "$i" -lt "$cnt" ]; do download "$id" --index "$i" --dir "$dir"; i=$((i+1)); done; return 0
  fi
  idx="${idx:-0}"
  if dry; then call GET "/jobs/$id/downloads/$idx" -o "$dir/${id}_$idx"; return 0; fi
  mkdir -p "$dir"; tmp="$dir/.${id}_$idx.part"; hdr="$dir/.${id}_$idx.hdr"
  call GET "/jobs/$id/downloads/$idx" -D "$hdr" -o "$tmp"
  ext="$(grep -i '^content-type:' "$hdr" | tail -n1 | tr -d '\r' | awk '{print $2}' | sed 's#.*/##;s/;.*//;s/jpeg/jpg/')"
  out="$dir/${id}_$idx.${ext:-bin}"; [ ! -e "$out" ] || out="$dir/${id}_${idx}_$(date +%H%M%S).${ext:-bin}"
  mv "$tmp" "$out"; rm -f "$hdr"; echo "$out"
}

cmd="${1:-}"; [ $# -gt 0 ] && shift
case "$cmd" in
  status) call GET /status;;
  submit-image) submit image "$@";;
  submit-video) submit video "$@";;
  upload) upload "$@";;
  job) [ $# -eq 1 ] || die "job JOB_ID"; valid_id "$1"; call GET "/jobs/$1";;
  wait) wait_job "$@";;
  download) download "$@";;
  -h|--help|help) usage;;
  *) usage >&2; exit 2;;
esac
