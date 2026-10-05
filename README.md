# Music Looper

一个 Minecraft **1.20.1 / Forge** 的客户端模组：让原版音乐**连续循环播放**，
并且可以把**你自己的音乐**放进 `config` 文件夹里一起随机播放。

作者：**hemjj,蓝色大肥鱼** · 许可证：**GPL-3.0**（见 [LICENSE](LICENSE)）

---

## 功能

1. **原版音乐连续循环**
   一首原版背景音乐播完，0.5 秒内就会自动接上下一首，不再有几分钟的静默空档。
   曲目池与原版一致：会按你所在的生物群系 / 维度挑选音乐（主世界、下界、末地、创造模式、水下……）。

2. **第三方音乐文件夹（自动创建）**

   ```
   .minecraft/config/musiclooper/music/
   ```

   把音乐文件丢进去就行（支持子文件夹）。扫描是纯 I/O 操作，所以**不做定时轮询**：
   进入世界时会自动扫一次，之后加/删了歌就敲一次指令

   ```
   /musiclooper reload
   ```

   想看当前认出几首，用 `/musiclooper status`。

   | 格式 | 说明 |
   | --- | --- |
   | `.ogg` | 推荐，用 Minecraft 自带解码器，永远可用 |
   | `.mp3` | 已内置解码器（mp3spi），可直接播放 |
   | `.wav` / `.aiff` / `.au` | 用 Java 自带解码器 |

   无法解码的文件不会被计入曲目数量，也会在日志里给出提示（最多列 20 个，避免刷屏）。

3. **配置文件（自动生成）**

   ```
   .minecraft/config/musiclooper-client.toml
   ```

   ```toml
   [vanilla]
       # 原版音乐是否连续循环播放
       loopVanillaMusic = true

       # 第三方音乐足够多时，是否完全禁用原版音乐
       disableVanillaMusicWhenEnoughCustom = false

       # 上面那个选项的门槛（默认 5 首）
       customMusicThreshold = 5

   [custom]
       # 是否播放第三方音乐
       playCustomMusic = true

       # 第三方音乐的音量倍率（0.0 ~ 1.0），实际音量 = 该值 × 游戏内“音乐”音量滑块
       customMusicVolume = 1.0
   ```

### 播放规则

| 情况 | 行为 |
| --- | --- |
| `loopVanillaMusic = true`，没有第三方音乐 | 原版音乐一首接一首，永不冷场 |
| `loopVanillaMusic = true`，有第三方音乐 | 原版曲目和第三方音乐**随机交替**，始终连续播放 |
| `disableVanillaMusicWhenEnoughCustom = true` 且第三方音乐 ≥ `customMusicThreshold` | **完全只播第三方音乐**，原版音乐被拦下 |
| `loopVanillaMusic = false` | 原版保持自己的节奏，第三方音乐只在原版留下的空档里播放 |

第三方音乐播放期间，原版音乐管理器自己挑起的曲目会被拦掉，不会出现两首歌同时响的情况。

### 指令

| 指令 | 作用 |
| --- | --- |
| `/musiclooper reload` | 重新扫描音乐文件夹（加完歌敲一下就行） |
| `/musiclooper status` | 显示认出几首、前 10 首文件名、当前配置 |

---

## 编译

```bash
# 需要 JDK 17 或更高
./gradlew build
```

产物：`build/libs/musiclooper-1.0.0.jar`，丢进 `.minecraft/mods/` 即可（需要 Forge 47.x）。

> 在 Termux / Android 上编译时，`build.gradle` 特意没有使用 Gradle 的 java toolchain
> （Android 的 bionic 环境下 Gradle 无法探测或下载 JDK 工具链），
> 而是直接用当前 JDK 编译，并把字节码限制为 Java 17。

### 在 Termux / Android 上手动编译（本仓库里那个 jar 的实际做法）

ForgeGradle 的 MCP 流水线在 Android 的 bionic 环境里跑不通：
`com.mojang:patchy` 解析失败、`mergeMappings/output.jar` 会被写坏、
forgeflower 反编译要 `-Xmx4G` 而设备只给得了约 1.5G 堆。所以这个 jar 是手工编出来的：

1. 用 **FART** 把官方 `client.jar` 反混淆成 mojmap 版本，作为编译类路径；
2. 用 `javac`（JDK 17，`--release 17`）直接编译 `src/main/java`；
3. 再用 **FART** 加一份自己合成的 `mojmap → SRG` 映射，把产物重混淆成运行时命名。

第 3 步的映射来自 `mcp_config` 的 `config/joined.tsrg`（obf→srg 成员名）与 Mojang 的
`client_mappings.txt`（mojmap→obf）合成，**类名保持 mojmap 不变，只重命名成员**——
这正是 1.20.1 Forge 的运行时命名空间
（线上崩溃日志里的 `net.minecraft.client.Minecraft.m_91374_ ~[client-…-srg.jar]` 就是它）。

编译期 classpath = 反混淆后的客户端 + `forge-1.20.1-47.4.20-universal.jar` + Forge 运行时库
（`fmlcore` / `fmlloader` / `javafmllanguage` / `eventbus` / `slf4j-api` / `night-config` 等）。

---

## 实现说明（给想改代码的人）

* `MusicController` —— 音乐调度：决定下一首放原版还是第三方，并检测曲目何时结束。
* `VanillaMusicProvider` —— 复刻原版的取曲逻辑（生物群系音乐 / 创造模式 / 水下）。
* `CustomMusicPlayer` —— 第三方音乐：解码线程 + 渲染线程推流，音量跟随游戏“音乐”滑块。
* `CustomMusicLibrary` —— `config/musiclooper/music` 的创建与扫描（指令触发，不轮询）。
* `OpenAlSink` —— 把 PCM 交给游戏自己的 OpenAL 设备播放。
* `audio/` —— 解码器：`.ogg` 用 Minecraft 的 `OggAudioStream`，`.mp3` 用内置 mp3spi，
  其余用 JavaSound。

全程只使用公开 API + Forge 事件（`PlaySoundEvent`、`TickEvent`、`SoundManager#isActive`、
`RegisterClientCommandsEvent`），**没有 Mixin，也没有反射内部字段**，所以不会因为 MC 混淆名变化而失效。

### 两个坑（踩过，记一下）

1. **为什么第三方音乐不走 `javax.sound.sampled` 播放**
   手机启动器自带的 JRE 里没有 `libjsound.so`，JavaSound 只剩“读文件”的能力，
   `AudioSystem.getSourceDataLine(...)` 必然失败。游戏自己的声音走 OpenAL，
   所以这里复用同一个 OpenAL 上下文——而且所有 AL 调用都留在**渲染线程**：
   Minecraft 初始化声音引擎时就是在渲染线程上 `alcMakeContextCurrent` 的。

2. **为什么 mp3 不通过 `AudioSystem` 打开**
   JavaSound 的解码器是「`META-INF/services` + 线程上下文类加载器」注册的，
   Forge 里模组 jar 不一定在那个类加载器上，实测会误报
   `UnsupportedAudioFileException: File of unsupported format`。
   现在直接 `new MpegAudioFileReader()`，完全绕开这张注册表。
   也因此 jar 里的 mp3spi 去掉了 `META-INF/services` 文件：
   `ServiceConfigurationError` 是 `Error` 不是 `Exception`，一旦被别的模组重复注册就会很麻烦。
