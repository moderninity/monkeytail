#!/usr/bin/env bash
# Builds Monkey Tail for NeoForge 1.21.1 with no Gradle and no network, by compiling straight
# against jars already on this machine:
#   - Minecraft 1.21.1 patched with NeoForge: the neoformruntime "compiledWithNeoForge" artifact
#   - the NeoForge API and its libraries: the Prism Launcher shared library store
#   - Sophisticated Backpacks: the instance's own jar, for the optional worn-bag lookup only
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
JDK="${JDK:-/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin}"
LIB="${PRISM_LIBS:-/d/Games/Modding/PrismLauncher/libraries}"
MODS="${MODS_DIR:-/d/Games/Modding/PrismLauncher/instances/Cosmic Ambition 1211/minecraft/mods}"

# Compile against the OLDEST NeoForge 21.1 in the store, not the newest. Building against an old
# API and running on a newer one is safe; the reverse is not, so this keeps the jar usable on
# every 21.1.x from here up, including the 21.1.249 the instance currently runs.
NEOFORGE_VERSION="${NEOFORGE_VERSION:-21.1.233}"

VERSION="1.0.0"
OUT="$HERE/build/classes"
DIST="$HERE/output"
JAR="$DIST/monkeytail-neoforge-1.21.1-$VERSION.jar"

rm -rf "$OUT"; mkdir -p "$OUT" "$DIST" "$HERE/build"

NFRT_CACHE="${NFRT_CACHE:-$HOME/.gradle/caches/neoformruntime}"
MC_JAR="$(ls -t "$NFRT_CACHE"/intermediate_results/compiledWithNeoForge_*_output.jar 2>/dev/null | head -1)"
if [ -z "$MC_JAR" ]; then
    echo "error: no neoformruntime compiledWithNeoForge artifact found." >&2
    echo "       Run any NeoForge 1.21.1 Gradle build once to populate the cache." >&2
    exit 1
fi

UNIVERSAL="$LIB/net/neoforged/neoforge/$NEOFORGE_VERSION/neoforge-$NEOFORGE_VERSION-universal.jar"
[ -f "$UNIVERSAL" ] || { echo "error: missing $UNIVERSAL" >&2; exit 1; }

# --- compile classpath -------------------------------------------------------------------
# The Prism store holds several Minecraft versions and loaders side by side, so keep only the
# newest build of each artifact and drop everything belonging to another loader: Forge 1.20.1,
# the Fabric loader, the srg-named client, and Forge's Mixin. NeoForge's Mixin ships as
# net.fabricmc:sponge-mixin, so that one has to stay.
CP_LIST="$HERE/build/cp.list"
{
    echo "$MC_JAR"
    echo "$UNIVERSAL"
    find "$LIB" -name '*.jar' ! -name '*installer*' \
      | grep -v -e '/net/minecraftforge/' -e '\-srg\.jar$' -e '/org/spongepowered/mixin/' \
                -e '/net/fabricmc/fabric-loader/' -e '/net/fabricmc/intermediary/' \
                -e '/net/fabricmc/tiny-' -e '/net/fabricmc/access-widener/' \
                -e '/net/neoforged/neoforge/' \
      | awk -F/ '{v=$(NF-1); a=$0; sub("/[^/]*$","",a); sub("/[^/]*$","",a); print a"\t"v"\t"$0}' \
      | sort -t$'\t' -k1,1 -k2,2V \
      | awk -F'\t' '{last[$1]=$3} END {for (k in last) print last[k]}'
    # Optional dependency: needed to compile SophisticatedWorn, never required at runtime.
    find "$MODS" -maxdepth 1 -name 'sophisticatedbackpacks-*.jar' | sort -V | tail -1
    find "$MODS" -maxdepth 1 -name 'sophisticatedcore-*.jar' | sort -V | tail -1
} | while read -r p; do [ -n "$p" ] && cygpath -m "$p"; done > "$CP_LIST"

# javac on Windows needs the classpath in an argfile, quoted, or the ';' separators are eaten.
printf -- '-cp "%s"\n' "$(paste -sd';' - < "$CP_LIST")" > "$HERE/build/args.txt"
find "$HERE/src/main/java" -name '*.java' | while read -r p; do printf '"%s"\n' "$(cygpath -m "$p")"; done > "$HERE/build/sources.txt"

"$JDK/javac" -encoding UTF-8 --release 21 -nowarn -proc:none \
    "@$(cygpath -m "$HERE/build/args.txt")" \
    -d "$(cygpath -m "$OUT")" \
    "@$(cygpath -m "$HERE/build/sources.txt")"

# --- package -----------------------------------------------------------------------------
cp -r "$HERE/src/main/resources/." "$OUT/"
# MIT asks that the notice travel with copies, so the jar carries one. Copied from the repo root
# at build time rather than kept a second time under resources/, so there is one file to edit.
cp "$HERE/LICENSE" "$OUT/LICENSE"
rm -f "$JAR"
"$JDK/jar" --create --file "$JAR" --manifest "$HERE/src/main/manifest.mf" -C "$OUT" .

# --- static checks that catch what compiling does not -------------------------------------
# eventbus_check catches a listener registration that compiles but kills mod loading;
# linkcheck catches a class or method that exists in the build but not at runtime.
# They live outside this repo; skip them rather than fail if they are not beside it.
TOOLS="${PORT_TOOLS:-$HERE/../port-tools}"
if [ -f "$TOOLS/eventbus_check.py" ]; then
    python "$TOOLS/eventbus_check.py" "$JAR" || exit 1
    python "$TOOLS/linkcheck.py" "$JAR" "$CP_LIST" || exit 1
else
    echo "note: port-tools not found at $TOOLS - static checks skipped (set \$PORT_TOOLS)" >&2
fi

echo
echo "built: $JAR   (compiled against NeoForge $NEOFORGE_VERSION)"
"$JDK/jar" --list --file "$JAR" | sed 's|^|  |'
