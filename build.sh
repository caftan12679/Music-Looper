#!/usr/bin/env bash
# =============================================================================
# Music Looper —— NeoForge 26.1.2 手工构建脚本
#
# 为什么不用 Gradle？
#   NeoGradle 的 build 要走完整 NeoForm 流水线：下载 MC → 合并 → 反编译整份
#   Minecraft → 打补丁 → 再重新编译，需要 2~3 GB 磁盘、几小时 CPU，手机上不现实。
#   而 26.x 的 Minecraft 与 NeoForge 本身都已经是**未混淆**的（官方 mojmap 名），
#   编译产物就是成品、不需要 reobf / remap，所以直接用 javac 对着
#   client.jar + neoforge-universal.jar 编译即可。
#
# 用法：
#   bash build.sh
# 产物：
#   build/libs/music-looper-neoforge-26.1.2-1.0.0.jar
# =============================================================================
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# ---------------------------------------------------------------- 版本号 ----
MOD_VERSION="1.0.0"
MC_VERSION="26.3"
NEO_VERSION="26.3.0.101"

# ---------------------------------------------------------------- 路径 ------
FCL_LIBS="/storage/emulated/0/FCL/.minecraft/libraries"   # 启动器下载的依赖库
MC_CLIENT_JAR="$HOME/mc263/client-26.3.jar"   # 26.3 客户端（未混淆）
GRADLE_CACHE="$HOME/.gradle/caches/modules-2/files-2.1"   # 之前 Fabric 版构建留下的依赖
WORK="$HOME/.ml-neoforge-263-build"                           # 外部工作目录，不污染工程

# ---------------------------------------------------------------- 工具链 ----
# 必须用 JDK 25 编译：MC 26.x 的 class 是 Java 25（major 69）。
# 注意不要写成 ${JAVA_HOME:-...}：Termux 里 JAVA_HOME 常被其它项目设成 21，
# 这里用专门的 ML_JAVA_HOME 覆盖，避免被外部环境污染。
JAVA_HOME="${ML_JAVA_HOME:-$PREFIX/lib/jvm/java-25-openjdk}"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"
export JAVA_HOME

if [ ! -x "$JAVAC" ]; then
    echo "!! 找不到 JDK 25：$JAVAC" >&2
    echo "   可用的 JDK：" >&2
    ls "$PREFIX/lib/jvm" >&2
    exit 1
fi

echo "==> 定位编译依赖"

# --------------------------------------------------- NeoForge 本体 ----------
# universal jar 里只有 net.neoforged.neoforge.*（事件、配置、注册表……），
# FML 的 @Mod / IEventBus / FMLPaths 在另外的 jar 里，见下面。
NEO_JAR="$WORK/neoforge-$NEO_VERSION-universal.jar"
if [ ! -f "$NEO_JAR" ]; then
    mkdir -p "$WORK"
    # 优先复用本机已有的快照（maven.neoforged.net 在手机网络上很不稳定）
    LOCAL_SNAPSHOT="$HOME/mc263/neoforge-26.3.jar"
    if [ -f "$LOCAL_SNAPSHOT" ]; then
        echo "    复用本地快照：$LOCAL_SNAPSHOT"
        cp "$LOCAL_SNAPSHOT" "$NEO_JAR"
    else
        echo "    下载 NeoForge $NEO_VERSION ..."
        curl -L --max-time 600 --retry 3 --retry-delay 2 -o "$NEO_JAR" \
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO_VERSION/neoforge-$NEO_VERSION-universal.jar"
    fi
fi

# --------------------------------------------------- 逐个定位依赖 ----------
# 在某个目录下找第一个匹配的 jar
find_jar() {
    find "$1" -name "$2" 2>/dev/null | head -1
}

# FML 加载器：@Mod / @EventBusSubscriber / FMLPaths / ModContainer
FML_JAR="$(find_jar "$GRADLE_CACHE/net.neoforged.fancymodloader/loader" 'loader-*.jar')"
# 事件总线：IEventBus / @SubscribeEvent
BUS_JAR="$FCL_LIBS/net/neoforged/bus/8.0.5/bus-8.0.5.jar"
# Dist 注解（在 mergetool 的 api 分类器里）
DIST_JAR="$FCL_LIBS/net/neoforged/mergetool/2.0.0/mergetool-2.0.0-api.jar"
# 指令树
BRIGADIER_JAR="$(find_jar "$FCL_LIBS/com/mojang/brigadier" 'brigadier-1.3.11.jar')"
# 第三方音乐借游戏自带的 OpenAL 播放
LWJGL_OPENAL_JAR="$(find_jar "$GRADLE_CACHE/org.lwjgl/lwjgl-openal" 'lwjgl-openal-3.4.1.jar')"
LWJGL_CORE_JAR="$(find_jar "$GRADLE_CACHE/org.lwjgl/lwjgl" 'lwjgl-3.4.1.jar')"

# MC 的 class 上带 @Nullable 之类的类型注解，
# javac 读这些 class 时必须能找到注解类，否则会抛 CompletionFailure
JSPECIFY_JAR="$(find_jar "$GRADLE_CACHE/org.jspecify/jspecify" 'jspecify-1.0.0.jar')"
LOG4J_JAR="$(find_jar "$FCL_LIBS/org/apache/logging/log4j/log4j-api" 'log4j-api-2.2*.jar')"
SLF4J_JAR="$(find_jar "$FCL_LIBS/org/slf4j/slf4j-api" 'slf4j-api-2.0.17.jar')"

# MP3 解码三件套：会被 shade 进成品 jar（见文件末尾）
JL_JAR="$(find_jar "$GRADLE_CACHE/com.googlecode.soundlibs/jlayer" 'jlayer-*.jar')"
MPS_JAR="$(find_jar "$GRADLE_CACHE/com.googlecode.soundlibs/mp3spi" 'mp3spi-*.jar')"
TRI_JAR="$(find_jar "$GRADLE_CACHE/com.googlecode.soundlibs/tritonus-share" 'tritonus-share-*.jar')"

for f in "$MC_CLIENT_JAR" "$NEO_JAR" "$FML_JAR" "$BUS_JAR" "$DIST_JAR" \
         "$BRIGADIER_JAR" "$LWJGL_OPENAL_JAR" "$LWJGL_CORE_JAR" "$JSPECIFY_JAR" \
         "$LOG4J_JAR" "$SLF4J_JAR" "$JL_JAR" "$MPS_JAR" "$TRI_JAR"; do
    if [ ! -f "$f" ]; then
        echo "!! 缺少依赖：$f" >&2
        exit 1
    fi
done

# 核心 classpath：先列出精确版本，保证解析到的是正确的那一个
CP="$MC_CLIENT_JAR:$NEO_JAR:$FML_JAR:$BUS_JAR:$DIST_JAR:$BRIGADIER_JAR"
CP="$CP:$LWJGL_OPENAL_JAR:$LWJGL_CORE_JAR:$JSPECIFY_JAR:$LOG4J_JAR:$SLF4J_JAR"
CP="$CP:$JL_JAR:$MPS_JAR:$TRI_JAR"

# 兜底：把依赖缓存里所有 jar 都再接上。
# 原因：MC 的 class 里大量引用 fastutil / guava / gson / datafixerupper 等库，
# javac 读签名时会把它们一个个拉进来，少一个就报 CompletionFailure。
# 331 个 jar、约 58KB 的 classpath，javac 是惰性解析的，不会拖慢编译。
FALLBACK_CP="$(find "$GRADLE_CACHE" -name '*.jar' \
    ! -name '*-sources.jar' ! -name '*-javadoc.jar' 2>/dev/null | tr '\n' ':')"
CP="$CP:${FALLBACK_CP%:}"

# ---------------------------------------------------------------- 清输出 ----
OUT_CLASSES="$PROJECT_DIR/build/classes"
OUT_JAR="$PROJECT_DIR/build/libs/music-looper-neoforge-$MC_VERSION-$MOD_VERSION.jar"
rm -rf "$OUT_CLASSES"
mkdir -p "$OUT_CLASSES" "$(dirname "$OUT_JAR")"

# ---------------------------------------------------------------- 编译 ------
echo "==> 编译 Java 源码（Java 25 / mojmap 未混淆）"
#   --release 25 : 生成 Java 25 字节码，和 MC 保持一致
#   -proc:none   : 不跑注解处理器（这里没有）
#   -nowarn      : 手机屏幕小，别刷警告
mkdir -p "$WORK"
SRC_LIST="$WORK/sources.txt"
find src/main/java -name '*.java' > "$SRC_LIST"

"$JAVAC" --release 25 -encoding UTF-8 -proc:none -nowarn \
    -d "$OUT_CLASSES" -cp "$CP" "@$SRC_LIST" 2>&1 | sed 's/^/    /' || {
        echo "!! 编译失败" >&2
        exit 1
    }
echo "    编译完成：$(find "$OUT_CLASSES" -name '*.class' | wc -l) 个 class"

# ---------------------------------------------------------------- 资源 ------
echo "==> 复制 resources"
if [ -d src/main/resources ]; then
    cp -r src/main/resources/. "$OUT_CLASSES"/
fi

# ------------------------------------------------ shade MP3 解码库 ---------
# 为什么不学 Fabric 版把 jar 塞进 META-INF/jars？
#   NeoForge 的内嵌依赖要走 JarJar 的 metadata.json，手写容易出错；
#   直接摊平（shade）更省事，而且 SPI 服务文件只需要合并一次。
echo "==> 合并 jlayer / mp3spi / tritonus-share"
TMP_SHADE="$WORK/shade"
rm -rf "$TMP_SHADE"
mkdir -p "$TMP_SHADE"
for j in "$JL_JAR" "$MPS_JAR" "$TRI_JAR"; do
    # 排除签名与 MANIFEST，避免互相覆盖导致 jar 损坏
    unzip -q -o "$j" \
        -x 'META-INF/MANIFEST.MF' 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' \
        -d "$TMP_SHADE"
done
cp -r "$TMP_SHADE"/. "$OUT_CLASSES"/

# ---------------------------------------------------------------- 打包 ------
echo "==> 打包"
"$JAR" --create --file "$OUT_JAR" -C "$OUT_CLASSES" .

echo
echo "==> 完成"
printf "    %-14s %s\n" "大小" "$(stat -c%s "$OUT_JAR") 字节"
printf "    %-14s %s\n" "产物" "$OUT_JAR"
