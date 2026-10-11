#!/usr/bin/env bash
# Checks that the version is bumped past the last release and that the F-Droid listing is within its limits. Prints
# the tag and the changelog as GitHub step outputs.
set -euo pipefail
export LC_ALL=C.UTF-8
cd "$(dirname "$0")/.."

name=$(sed -n "s/^ *versionName '\(.*\)'$/\1/p" app/build.gradle)
code=$(sed -n 's/^ *versionCode \([0-9]*\)$/\1/p' app/build.gradle)
listing=fastlane/metadata/android/en-US
failed=0

fail() {
    echo "$1" >&2
    failed=1
}

check_length() {
    local text
    if [[ ! -f $1 ]]; then
        fail "Missing $1"
        return
    fi
    text=$(<"$1")
    (( ${#text} <= $2 )) || fail "$1 has ${#text} characters, over the limit of $2"
}

[[ -n $name && -n $code ]] || { echo "No versionName or versionCode in app/build.gradle" >&2; exit 1; }
if git ls-remote --exit-code --tags origin "refs/tags/v$name" > /dev/null; then
    fail "v$name is already released. Bump versionName first."
fi
if previous=$(git describe --tags --abbrev=0 --match 'v*' 2> /dev/null); then
    previous_code=$(git show "$previous:app/build.gradle" | sed -n 's/^ *versionCode \([0-9]*\)$/\1/p')
    (( code > previous_code )) || fail "versionCode $code isn't above $previous's $previous_code. Bump versionCode first."
fi

check_length "$listing/title.txt" 50
check_length "$listing/short_description.txt" 80
check_length "$listing/full_description.txt" 4000
check_length "$listing/changelogs/$code.txt" 500
[[ $(<"$listing/short_description.txt") != *. ]] || fail "$listing/short_description.txt must not end with a period"

(( ! failed )) || exit 1
echo "tag=v$name"
echo "notes=$listing/changelogs/$code.txt"
