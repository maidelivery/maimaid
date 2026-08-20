#!/bin/sh
set -eu
build_number="$("$SRCROOT/../scripts/build-number.sh")"
info_plist="$TARGET_BUILD_DIR/$INFOPLIST_PATH"
/usr/libexec/PlistBuddy -c "Set :CFBundleVersion $build_number" "$info_plist"

