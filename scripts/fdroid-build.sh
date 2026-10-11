#!/usr/bin/env bash
# Writes the F-Droid recipe for the source's HEAD, lints it, and builds it as F-Droid does. Runs inside
# registry.gitlab.com/fdroid/fdroidserver:buildserver and copies the recipe, ready for fdroiddata, and the unsigned APK
# to the output directory. Given a signed APK, checks it against the build as F-Droid checks the APK in Binaries.
# shellcheck disable=SC1091
. /etc/profile
set -euo pipefail
git config --global --add safe.directory '*'

src=$(realpath "${1:?source repository}")
out=$(realpath -m "${2:?output directory}")
signed=${3:+$(realpath "$3")}
appid=io.github.probecat.airplanescheduler
name=$(sed -n "s/^ *versionName '\(.*\)'$/\1/p" "$src/app/build.gradle")
code=$(sed -n 's/^ *versionCode \([0-9]*\)$/\1/p' "$src/app/build.gradle")
commit=$(git -C "$src" rev-parse HEAD)
work=$(mktemp -d)

git clone --quiet --depth 1 https://gitlab.com/fdroid/fdroidserver.git "$work/fdroidserver"
export PATH="$work/fdroidserver:$PATH" PYTHONPATH="$work/fdroidserver"
mkdir -p "$out" "$work/fdroiddata/metadata"
cd "$work/fdroiddata"

printf 'sdk_path: %s\n' "$ANDROID_HOME" > config.yml
# Lint checks categories against fdroiddata's list. Naming them here skips loading each one's icon.
curl -fsSL https://gitlab.com/fdroid/fdroiddata/-/raw/master/config/categories.yml |
    python3 -c 'import sys, yaml; print(yaml.safe_dump({"categories": list(yaml.safe_load(sys.stdin))}))' >> config.yml
chmod 600 config.yml

recipe=metadata/$appid.yml
cat > "$recipe" <<RECIPE
Categories:
  - Connectivity
  - System
License: GPL-3.0-or-later
AuthorName: Rashed Arman
SourceCode: https://github.com/probecat/airplane-scheduler-app
IssueTracker: https://github.com/probecat/airplane-scheduler-app/issues
Changelog: https://github.com/probecat/airplane-scheduler-app/releases

AutoName: Airplane Scheduler

RepoType: git
Repo: https://github.com/probecat/airplane-scheduler-app.git
Binaries: https://github.com/probecat/airplane-scheduler-app/releases/download/v%v/airplane-scheduler.apk

Builds:
  - versionName: '$name'
    versionCode: $code
    commit: $commit
    subdir: app
    gradle:
      - yes

AllowedAPKSigningKeys: 5b845204dfcc20c19799eae5492ce308c3b581e72073759d7af81a8fdc341dbe

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: '$name'
CurrentVersionCode: $code
RECIPE
cp "$recipe" "$work/recipe.yml"
fdroid lint "$appid"
fdroid rewritemeta "$appid"
cp "$recipe" "$out/"

# Builds from the local checkout, without Binaries, which point to a release that may not exist yet. Edits the copy
# from before rewritemeta, which wraps Binaries onto a second line.
sed -e "s|^Repo: .*|Repo: file://$src|" -e '/^Binaries: /d' "$work/recipe.yml" > "$recipe"
fdroid fetch_repo "$appid:$code"
fdroid build --test --no-tarball "$appid:$code"
apk=$(find tmp -name "${appid}_*.apk" | head -1)
fdroid scanner --exit-code "$apk"
cp "$apk" "$out/"
echo "Built $out/$(basename "$apk")"

if [[ -n $signed ]]; then
    python3 -c 'import sys; from fdroidserver import common; common.read_config(); sys.exit(common.verify_apks(*sys.argv[1:]))' \
        "$signed" "$apk" "$work"
    echo "The signed APK matches F-Droid's build"
fi
