#!/bin/bash
# Upgrades the Samsung TV app (samsung-tv/, "Guest Welcome") on the TV whenever
# GitHub main has new commits touching samsung-tv/.
#
# Runs from its own clone outside ~/Desktop (background jobs may not read ~/Desktop),
# and installs as an in-place upgrade (vd_appinstall) so the app's saved data
# (guest name, message, paired phones) survives. It never uses `tz install`,
# which uninstalls first and wipes that data.
#
# Usage: samsung-autoupdate.sh [--once] [--verbose] [--dry-run]
#   --once     run a single check (the LaunchAgent runs it every 5 minutes anyway)
#   --verbose  also print log lines to the terminal
#   --dry-run  fetch, build and sign, but do not touch the TV or the state file

set -u
set -o pipefail

# GE_REPO_URL / GE_SUPPORT_DIR override these for testing only.
REPO_URL="${GE_REPO_URL:-https://github.com/hiteshramawat01-oss/WelcomeTV.git}"
APP_ID="GuestExpTV.GuestWelcome"
SUPPORT_DIR="${GE_SUPPORT_DIR:-$HOME/Library/Application Support/GuestExperience}"
CLONE_DIR="$SUPPORT_DIR/WelcomeTV"
STATE_FILE="$SUPPORT_DIR/samsung-installed-commit"
LOCK_DIR="$SUPPORT_DIR/samsung-update.lock"
LOG_DIR="$HOME/Library/Logs/GuestExperience"
LOG_FILE="$LOG_DIR/samsung-update.log"
CONFIG_DIR="$HOME/.config/guestexperience"
CONFIG_FILE="$CONFIG_DIR/samsung.env"
TV_TMP_PATH="/home/owner/share/tmp/sdk_tools/tmp/GuestWelcome.wgt"

VERBOSE=0
DRY_RUN=0
for arg in "$@"; do
    case "$arg" in
        --once) ;;  # every run is a single check; kept for clarity on the command line
        --verbose) VERBOSE=1 ;;
        --dry-run) DRY_RUN=1 ;;
        -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
        *) echo "Unknown option: $arg" >&2; exit 2 ;;
    esac
done

# launchd starts us with a bare PATH.
export PATH="/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

mkdir -p "$SUPPORT_DIR" "$LOG_DIR"

log() {
    local line
    line="$(date '+%Y-%m-%d %H:%M:%S') $*"
    echo "$line" >> "$LOG_FILE"
    if [ "$VERBOSE" -eq 1 ]; then echo "$line"; fi
}

fail() { log "ERROR: $*"; exit 1; }

# ---- Settings ---------------------------------------------------------------
if [ ! -f "$CONFIG_FILE" ]; then
    mkdir -p "$CONFIG_DIR"
    cat > "$CONFIG_FILE" <<'EOF'
# Settings for tools/samsung-autoupdate.sh (Guest Experience TV, Samsung app).
# Fill these in, then the next run will use them.

# The TV's IP address on your Wi-Fi (TV: Settings > General > Network > Network Status).
#TV_IP=192.168.1.50

# Name of the Samsung certificate profile made in the VS Code Tizen extension
# (it must include the TV's DUID).
#PROFILE="My Samsung Profile"

# Optional: override tool locations.
#TZ="$HOME/.tizen-extension-platform/server/sdktools/data/tools/tizen-core/tz"
#SDB="$HOME/tizentv-tools/sdb/sdb"
#PROFILES_XML="$HOME/.tizen-extension-platform/server/sdktools/sdk-data/profile/profiles.xml"
EOF
    log "Created $CONFIG_FILE — set TV_IP and PROFILE there, then run again."
    exit 0
fi

TV_IP=""; PROFILE=""
# shellcheck disable=SC1090
. "$CONFIG_FILE"
TZ="${TZ:-$HOME/.tizen-extension-platform/server/sdktools/data/tools/tizen-core/tz}"
SDB="${SDB:-$HOME/tizentv-tools/sdb/sdb}"
PROFILES_XML="${PROFILES_XML:-$HOME/.tizen-extension-platform/server/sdktools/sdk-data/profile/profiles.xml}"

[ -n "$TV_IP" ] || fail "TV_IP is not set in $CONFIG_FILE"
[ -n "$PROFILE" ] || fail "PROFILE is not set in $CONFIG_FILE"
[ -x "$TZ" ] || fail "Tizen tz tool not found at $TZ"
[ -x "$SDB" ] || fail "sdb not found at $SDB"
[ -f "$PROFILES_XML" ] || fail "Certificate profiles not found at $PROFILES_XML"
DEVICE="$TV_IP:26101"

# ---- One run at a time ------------------------------------------------------
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    # A lock older than 30 minutes is left over from a crashed run.
    if [ -n "$(find "$LOCK_DIR" -maxdepth 0 -mmin +30 2>/dev/null)" ]; then
        rmdir "${LOCK_DIR:?}" 2>/dev/null
        mkdir "$LOCK_DIR" 2>/dev/null || { log "Another update is running; skipping."; exit 0; }
    else
        log "Another update is running; skipping."
        exit 0
    fi
fi
WORK_DIR=""
cleanup() {
    if [ -n "$WORK_DIR" ] && [ -d "$WORK_DIR" ]; then rm -rf "${WORK_DIR:?}"; fi
    rmdir "${LOCK_DIR:?}" 2>/dev/null
}
trap cleanup EXIT

# ---- Get the latest main ----------------------------------------------------
if [ ! -d "$CLONE_DIR/.git" ]; then
    log "Cloning $REPO_URL"
    git clone --quiet "$REPO_URL" "$CLONE_DIR" >> "$LOG_FILE" 2>&1 || fail "git clone failed"
fi
cd "$CLONE_DIR" || fail "Cannot enter $CLONE_DIR"
if ! git fetch --quiet origin main >> "$LOG_FILE" 2>&1; then
    log "git fetch failed (offline?); will retry next run."
    exit 0
fi
git reset --quiet --hard origin/main >> "$LOG_FILE" 2>&1 || fail "git reset to origin/main failed"

COMMIT="$(git log -1 --format=%H -- samsung-tv)"
if [ -z "$COMMIT" ]; then log "main has no samsung-tv/ folder yet; nothing to install."; exit 0; fi
INSTALLED="$(cat "$STATE_FILE" 2>/dev/null || true)"
if [ "$COMMIT" = "$INSTALLED" ]; then
    log "Up to date (samsung-tv at ${COMMIT:0:7})."
    exit 0
fi
COUNT="$(git rev-list --count HEAD -- samsung-tv)"
VERSION="1.2.$COUNT"
log "New samsung-tv commit ${COMMIT:0:7} (installed: $( [ -n "$INSTALLED" ] && echo "${INSTALLED:0:7}" || echo none )); building version $VERSION"

# ---- Build and sign ---------------------------------------------------------
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/samsung-update.XXXXXX")" || fail "mktemp failed"
APP_DIR="$WORK_DIR/app"
cp -R samsung-tv "$APP_DIR" || fail "Copying samsung-tv failed"

# Stamp the widget version (only the <widget ... version="..."> attribute).
/usr/bin/perl -0pi -e 's/(<widget\b[^>]*?\bversion=")[^"]*(")/${1}'"$VERSION"'${2}/s' "$APP_DIR/config.xml" \
    || fail "Could not stamp version in config.xml"
grep -q "version=\"$VERSION\"" "$APP_DIR/config.xml" || fail "Version stamp not found in config.xml"

UNSIGNED="$WORK_DIR/GuestWelcome-unsigned.wgt"
SIGNED="$WORK_DIR/GuestWelcome.wgt"
(
    cd "$APP_DIR" || exit 1
    shopt -s nullglob
    files=(config.xml *.html *.css *.js icon.png)
    for d in fonts vendor scenes scene; do [ -d "$d" ] && files+=("$d"); done
    for f in *.scene *.json; do files+=("$f"); done
    zip -qr -X "$UNSIGNED" "${files[@]}" -x '*.DS_Store' -x 'README.md'
) >> "$LOG_FILE" 2>&1 || fail "zip failed"

"$TZ" pack -t wgt -s "$PROFILE" -p "$PROFILES_XML" -b "$UNSIGNED" -o "$SIGNED" >> "$LOG_FILE" 2>&1 \
    || fail "Signing with profile \"$PROFILE\" failed"
[ -s "$SIGNED" ] || fail "Signed package was not created"
log "Signed $(basename "$SIGNED") ($(wc -c < "$SIGNED" | tr -d ' ') bytes)"

if [ "$DRY_RUN" -eq 1 ]; then
    log "Dry run: would install ${COMMIT:0:7} as $VERSION on $DEVICE; TV and state file left alone."
    exit 0
fi

# ---- Install on the TV ------------------------------------------------------
CONNECT_OUT="$("$SDB" connect "$DEVICE" 2>&1)"
log "sdb connect: $CONNECT_OUT"
if ! "$SDB" devices 2>/dev/null | grep -q "^$DEVICE[[:space:]]*device"; then
    log "TV at $DEVICE is not reachable (off, or not on this Wi-Fi?); will retry next run."
    exit 0
fi

PUSH_OUT="$("$SDB" -s "$DEVICE" push "$SIGNED" "$TV_TMP_PATH" 2>&1)" \
    || fail "Copying the package to the TV failed: $PUSH_OUT"
log "sdb push: $(echo "$PUSH_OUT" | tail -1)"

INSTALL_OUT="$("$SDB" -s "$DEVICE" shell 0 vd_appinstall "$APP_ID" "$TV_TMP_PATH" 2>&1)"
echo "$INSTALL_OUT" >> "$LOG_FILE"
if ! echo "$INSTALL_OUT" | grep -q "install completed"; then
    REASON="$(echo "$INSTALL_OUT" | grep -iE 'error|fail' | tail -3 | tr '\n' ' ')"
    fail "vd_appinstall did not complete: ${REASON:-see log}"
fi
log "Installed ${COMMIT:0:7} as version $VERSION"

LAUNCH_OUT="$("$SDB" -s "$DEVICE" shell 0 was_execute "$APP_ID" 2>&1)"
log "Launch: $(echo "$LAUNCH_OUT" | tail -1)"

echo "$COMMIT" > "$STATE_FILE"
log "Done."
exit 0
