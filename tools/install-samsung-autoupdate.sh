#!/bin/bash
# Installs, removes or shows the LaunchAgent that runs samsung-autoupdate.sh every 5 minutes.
#
# Usage: install-samsung-autoupdate.sh install|uninstall|status
#
# The update script is copied to ~/Library/Application Support/GuestExperience/bin, because
# macOS privacy can stop background jobs from reading files under ~/Desktop.
# Run `install` again after changing tools/samsung-autoupdate.sh to copy the new version.

set -u

LABEL="com.guestexperience.samsung-update"
TOOLS_DIR="$(cd "$(dirname "$0")" && pwd)"
SUPPORT_DIR="$HOME/Library/Application Support/GuestExperience"
BIN_DIR="$SUPPORT_DIR/bin"
SCRIPT_DEST="$BIN_DIR/samsung-autoupdate.sh"
PLIST_DEST="$HOME/Library/LaunchAgents/$LABEL.plist"
LOG_FILE="$HOME/Library/Logs/GuestExperience/samsung-update.log"
DOMAIN="gui/$(id -u)"

escape_xml() { sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g'; }

is_loaded() { launchctl print "$DOMAIN/$LABEL" >/dev/null 2>&1; }

case "${1:-}" in
    install)
        mkdir -p "$BIN_DIR" "$HOME/Library/LaunchAgents" "$HOME/Library/Logs/GuestExperience"
        cp "$TOOLS_DIR/samsung-autoupdate.sh" "$SCRIPT_DEST" && chmod 755 "$SCRIPT_DEST" || exit 1
        script_xml="$(printf '%s' "$SCRIPT_DEST" | escape_xml)"
        home_xml="$(printf '%s' "$HOME" | escape_xml)"
        sed -e "s|__SCRIPT__|$script_xml|g" -e "s|__HOME__|$home_xml|g" \
            "$TOOLS_DIR/$LABEL.plist" > "$PLIST_DEST" || exit 1
        plutil -lint "$PLIST_DEST" >/dev/null || { echo "Generated plist is invalid: $PLIST_DEST"; exit 1; }
        if is_loaded; then launchctl bootout "$DOMAIN/$LABEL" 2>/dev/null; fi
        launchctl bootstrap "$DOMAIN" "$PLIST_DEST" || { echo "launchctl bootstrap failed"; exit 1; }
        echo "Installed. Checks GitHub every 5 minutes; log: $LOG_FILE"
        ;;
    uninstall)
        if is_loaded; then launchctl bootout "$DOMAIN/$LABEL"; fi
        if [ -f "$PLIST_DEST" ]; then rm -f "${PLIST_DEST:?}"; fi
        if [ -f "$SCRIPT_DEST" ]; then rm -f "${SCRIPT_DEST:?}"; fi
        echo "Uninstalled. (Clone, state file and log are kept in $SUPPORT_DIR and ~/Library/Logs/GuestExperience.)"
        ;;
    status)
        if is_loaded; then
            echo "LaunchAgent: loaded"
            launchctl print "$DOMAIN/$LABEL" | grep -E '^\s*(state|last exit code|runs) ' | sed 's/^\s*/  /'
        else
            echo "LaunchAgent: not loaded"
        fi
        if [ -f "$SUPPORT_DIR/samsung-installed-commit" ]; then
            echo "Installed on TV: $(cut -c1-7 "$SUPPORT_DIR/samsung-installed-commit")"
        fi
        if [ -f "$LOG_FILE" ]; then echo "Last log lines:"; tail -5 "$LOG_FILE" | sed 's/^/  /'; fi
        ;;
    *)
        echo "Usage: $0 install|uninstall|status" >&2
        exit 2
        ;;
esac
