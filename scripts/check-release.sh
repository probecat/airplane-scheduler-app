#!/usr/bin/env bash
# Checks the version against origin's latest release, and the F-Droid listing.
set -euo pipefail
export LC_ALL=C.UTF-8
cd "$(dirname "$0")/.."

name=$(sed -n "s/^ *versionName '\(.*\)'$/\1/p" app/build.gradle)
code=$(sed -n 's/^ *versionCode \([0-9]*\)$/\1/p' app/build.gradle)
listing=fastlane/metadata/android/en-US
notes=$listing/changelogs/$code.txt
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
    [[ -n ${text//[[:space:]]/} ]] || fail "$1 is empty"
    (( ${#text} <= $2 )) || fail "$1 has ${#text} characters, over the limit of $2"
}

[[ -n $name && -n $code ]] || { echo "No versionName or versionCode in app/build.gradle" >&2; exit 1; }
[[ $name =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(\.[1-9][0-9]*)?$ ]] ||
    { echo "versionName $name isn't X.Y or X.Y.Z, with Z above 0" >&2; exit 1; }

tags=$(git ls-remote --tags --refs origin 'refs/tags/v*' | sed 's|.*refs/tags/v||')
latest=$(grep -E '^[0-9]+(\.[0-9]+)*$' <<< "$tags" | sort -V | tail -1 || true)
release=true
if [[ -n $latest ]]; then
    git fetch --quiet --no-tags origin "refs/tags/v$latest"
    latest_commit=$(git rev-parse 'FETCH_HEAD^{commit}')
    latest_code=$(git show "$latest_commit:app/build.gradle" | sed -n 's/^ *versionCode \([0-9]*\)$/\1/p')
    if [[ $name == "$latest" ]]; then
        release=false
        (( code == latest_code )) || fail "versionCode $code differs from v$latest's $latest_code. Bump versionName too."
    else
        [[ $(printf '%s\n' "$latest" "$name" | sort -V | tail -1) == "$name" ]] ||
            fail "versionName $name isn't above the latest release, v$latest"
        grep -qxF "$name" <<< "$tags" && fail "v$name is already released"
        (( code > latest_code )) || fail "versionCode $code isn't above v$latest's $latest_code"
        ! git cat-file -e "$latest_commit:$notes" 2> /dev/null || fail "$notes already shipped in v$latest"
    fi
fi

check_length "$listing/title.txt" 50
check_length "$listing/short_description.txt" 80
check_length "$listing/full_description.txt" 4000
check_length "$notes" 500
[[ $(<"$listing/short_description.txt") != *. ]] || fail "$listing/short_description.txt must not end with a period"

(( ! failed )) || exit 1
echo "release=$release"
echo "tag=v$name"
echo "previous=${latest:+v$latest}"
echo "notes=$notes"
