#!/bin/bash
# ============================================================================
# Music Looper (Fabric) 手工构建脚本
#
# 为什么不用 Gradle：ForgeGradle / Loom 的构建流水线在 Android 的 bionic 环境里
# 跑不通（native-platform 的 SystemInfo 不可用、反编译要 4G 堆而设备只给约 1.5G）。
# 所以这里用「手工三段式」：编译 → 重映射 → 打包。
#
# 依赖：JDK 17、Termux 的 curl/unzip/jar、以及 ~/fabric/build-tools 下的工具与映射。
# ============================================================================
set -e

BUILD_TOOLS="$HOME/fabric/build-tools"
PROJECT="$HOME/fabric/musiclooper-1.21.1"
JAVAC="$PREFIX/lib/jvm/java-21-openjdk/bin/javac"

MC_MOJMAP="$HOME/fabric/build-tools/mc-mojmap-1.21.1.jar"          # FART 反混淆后的客户端（mojmap）
FABRIC_LOADER="$BUILD_TOOLS/fabric-loader-0.19.5.jar"
SPONGE_MIXIN="$BUILD_TOOLS/sponge-mixin-0.17.5.jar"
FART="$BUILD_TOOLS/fart.jar"
OBF_CLIENT="$BUILD_TOOLS/client-1.21.1.jar"

cd "$BUILD_TOOLS"

# --------------------------------------------------------------------------
# 1) 编译 Fabric API 的编译桩（单独输出，绝不进 jar）
# --------------------------------------------------------------------------
echo "==> 编译 API 桩"
rm -rf stubs-out-1.21.1 && mkdir -p stubs-out-1.21.1
BRIG=$(find "$HOME/.gradle/caches" -name 'brigadier-*.jar' | head -1)
"$JAVAC" -nowarn -proc:none -cp "$MC_MOJMAP:$BRIG" -d stubs-out-1.21.1 \
    $(find "$PROJECT/stubs" -name '*.java' | sort)

# --------------------------------------------------------------------------
# 2) 编译模组源码（源码是 mojmap 命名空间）
# --------------------------------------------------------------------------
echo "==> 编译模组"
DEPS=$(cat "$HOME/build-tools/cp.txt" | tr ':' '\n' | grep -v 'mc-mojmap-1.21.1.jar' | grep -v 'forge-1.20.1')
LIB_ARGS=""
for j in $DEPS; do LIB_ARGS="$LIB_ARGS --lib $j"; done

CP="stubs-out-1.21.1:$MC_MOJMAP:$(cat "$HOME/build-tools/cp.txt"):$FABRIC_LOADER:$SPONGE_MIXIN"
rm -rf mod-out-1.21.1 && mkdir -p mod-out-1.21.1
"$JAVAC" -encoding UTF-8 --release 21 -nowarn -proc:none -cp "$CP" -d mod-out-1.21.1 \
    $(find "$PROJECT/src/main/java" -name '*.java' | sort)

rm -f mod121-classes.jar
(cd mod-out-1.21.1 && jar --create --file ../mod121-classes.jar .)

# --------------------------------------------------------------------------
# 3) 两步重映射：mojmap -> obf -> intermediary
#
#    必须分两步！一步式（mojmap->intermediary 的合成映射）不做继承传播：
#    intermediary 把某些方法记在接口名下（例如 blockPosition 记在 class_5568），
#    一步式会生成 obf 名而不是 method_XXXX，运行时会 NoSuchMethodError。
#    FART 在第二步会自动做继承传播，结果与真实运行时一致。
# --------------------------------------------------------------------------
echo "==> 重映射 第一步 mojmap -> obf"
java -Xmx1400m -jar "$FART" --input mod121-classes.jar --output mod121-obf.jar \
    --map maps211/mojmap2obf.tsrg --threads 2 \
    --lib "$MC_MOJMAP" --lib "$FABRIC_LOADER" --lib "$SPONGE_MIXIN" $LIB_ARGS

echo "==> 重映射 第二步 obf -> intermediary"
java -Xmx1400m -jar "$FART" --input mod121-obf.jar --output mod121-intermediary.jar \
    --map maps211/obf2intermediary.tsrg --threads 2 \
    --lib "$OBF_CLIENT" --lib "$FABRIC_LOADER" --lib "$SPONGE_MIXIN" $LIB_ARGS

# --------------------------------------------------------------------------
# 4) 打包
# --------------------------------------------------------------------------
echo "==> 打包"
rm -rf pkg121 && mkdir -p pkg121 && cd pkg121
unzip -q ../mod121-intermediary.jar
cp "$PROJECT/src/main/resources/fabric.mod.json" .
cp "$PROJECT/src/main/resources/musiclooper.mixins.json" .
for j in "$HOME/mp3test/nospi/mp3spi-1.9.5.4.jar" \
         "$HOME/mp3test/nospi/jlayer-1.0.1.4.jar" \
         "$HOME/mp3test/nospi/tritonus-share-0.3.7.4.jar"; do
    unzip -q -o "$j" -x 'META-INF/MANIFEST.MF' 'module-info.class' 2>/dev/null || true
done
rm -f ../musiclooper-fabric-1.21.1-1.1.0-hotfix.jar
jar --create --file ../musiclooper-fabric-1.21.1-1.1.0-hotfix.jar \
    --manifest <(printf 'Manifest-Version: 1.0\nImplementation-Title: musiclooper\nImplementation-Version: 1.1.0-hotfix\n\n') \
    -C . .

echo
echo "完成：$BUILD_TOOLS/musiclooper-fabric-1.21.1-1.1.0-hotfix.jar"
