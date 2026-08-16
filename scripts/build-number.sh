#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/.." && pwd)

if [ -n "${MAIMAID_BUILD_NUMBER:-}" ]; then
    build_number=$MAIMAID_BUILD_NUMBER
else
    build_number=$(git -C "$repository_root" rev-list --count HEAD)
fi

case $build_number in
    ''|*[!0-9]*)
        echo "Invalid maimaid build number: $build_number" >&2
        exit 1
        ;;
esac

if [ "$build_number" -lt 1 ]; then
    echo "maimaid build number must be positive" >&2
    exit 1
fi

printf '%s\n' "$build_number"
