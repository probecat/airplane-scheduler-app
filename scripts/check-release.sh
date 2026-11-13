#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C.UTF-8
cd "$(dirname "$0")/.."

die() {
    echo "$1" >&2
    exit 1
}

limit() {
    [[ -s $1 ]] || die "$1 is missing or empty"
    local text
    text=$(<"$1")
    (( ${#text} <= $2 )) || die "$1 has ${#text} characters, over the limit of $2"
}

name=$(sed -n "s/^ *versionName '\(.*\)'$/\1/p" app/build.gradle)
code=$(sed -n 's/^ *versionCode \([0-9]*\)$/\1/p' app/build.gradle)
[[ -n $name && -n $code ]] || die "No versionName or versionCode in app/build.gradle"
listing=fastlane/metadata/android/en-US
notes=$listing/changelogs/$code.txt

latest=$(git for-each-ref --count=1 --sort=-v:refname --format='%(refname:short)' 'refs/tags/v[0-9]*')
release=true
if [[ -n $latest ]]; then
    latest_code=$(git show "$latest:app/build.gradle" | sed -n 's/^ *versionCode \([0-9]*\)$/\1/p')
    if [[ v$name == "$latest" ]]; then
        release=false
        (( code == latest_code )) || die "versionCode $code differs from $latest's $latest_code. Bump versionName too."
    else
        [[ $(printf '%s\n' "$latest" "v$name" | sort -V | tail -1) == "v$name" ]] ||
            die "versionName $name isn't above the latest release, $latest"
        (( code > latest_code )) || die "versionCode $code isn't above $latest's $latest_code"
    fi
fi

limit "$listing/title.txt" 50
limit "$listing/short_description.txt" 80
limit "$listing/full_description.txt" 4000
limit "$notes" 500
[[ $(<"$listing/short_description.txt") != *. ]] || die "$listing/short_description.txt must not end with a period"

echo "release=$release"
echo "tag=v$name"
echo "previous=$latest"
echo "notes=$notes"
