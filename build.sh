#!/bin/bash
# ============================================================================
# Music Looper (NeoForge 1.21.1) 手工构建脚本
#
# 为什么不用 Gradle：构建设备是 Android/Termux，NeoGradle 在那里跑不通。
# 好消息是 NeoForge 1.21.1 的运行时命名空间就是 Mojang 官方名（mojmap），
# 所以**不需要任何重映射**，编译出来直接就是成品。
#
# 依赖：JDK 21、以及 ~/neo/build-tools 下的 NeoForge 全家桶与映射。
# ============================================================================
set -e
cd "$(dirname "$0")"

JAVAC="$PREFIX/lib/jvm/java-21-openjdk/bin/javac"
export BT="$HOME/neo/build-tools"
CP="$(cat "$BT/cptotal.txt")"

OUT="$BT/mod-out"
PKG="$BT/pkg"

echo "==> 编译"
rm -rf "$OUT" && mkdir -p "$OUT"
"$JAVAC" -encoding UTF-8 --release 21 -nowarn -proc:none -cp "$CP" -d "$OUT" \
    $(find src/main/java -name '*.java' | sort)

echo "==> 打包（无需重映射）"
rm -rf "$PKG" && mkdir -p "$PKG/META-INF"
cp -a "$OUT/com" "$PKG/"

python3 - <<'PY'
import os
props = {
 'loader_version_range': '[1,)', 'mod_license': 'GPL-3.0', 'mod_id': 'musiclooper',
 'mod_version': '1.1.0-hotfix', 'mod_name': 'Music Looper', 'neo_version': '21.1.259',
 'minecraft_version_range': '[1.21.1]',
}
s = open('src/main/resources/META-INF/neoforge.mods.toml', encoding='utf-8').read()
for k, v in props.items():
    s = s.replace('${%s}' % k, v)
open(os.path.join(os.environ['BT'], 'pkg', 'META-INF', 'neoforge.mods.toml'), 'w', encoding='utf-8').write(s)
PY

cd "$PKG"
for j in "$HOME/mp3test/nospi/mp3spi-1.9.5.4.jar" \
         "$HOME/mp3test/nospi/jlayer-1.0.1.4.jar" \
         "$HOME/mp3test/nospi/tritonus-share-0.3.7.4.jar"; do
    unzip -q -o "$j" -x 'META-INF/MANIFEST.MF' 'module-info.class' 2>/dev/null || true
done
rm -f "$BT/musiclooper-neoforge-1.21.1-1.1.0-hotfix.jar"
jar --create --file "$BT/musiclooper-neoforge-1.21.1-1.1.0-hotfix.jar" \
    --manifest <(printf 'Manifest-Version: 1.0\nImplementation-Title: musiclooper\nImplementation-Version: 1.0.0\n\n') -C . .

echo
echo "完成：$BT/musiclooper-neoforge-1.21.1-1.1.0-hotfix.jar"
