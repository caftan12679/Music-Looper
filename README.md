# Music Looper（Fabric 版）

让 Minecraft 的音乐**永远不停**，还能放你自己的歌。

> 适用版本：Minecraft **1.20.1** + Fabric Loader **0.19.5** · 纯客户端模组
> 需要同时安装 **Fabric API**

---

## 这个模组做什么

**1. 原版音乐连续播放**

原版背景音乐播完会静默几分钟才有下一首。装上这个模组后，一首接一首，
几乎感觉不到间断。选曲逻辑和原版一样——会按你所在的生物群系、维度来挑音乐。

**2. 放你自己的音乐**

游戏里会自动生成这个文件夹：

```
.minecraft/config/musiclooper/music/
```

把你喜欢的歌丢进去就行（支持子文件夹），它们会被加进随机播放列表，
和原版音乐一起交替播放。

支持的格式：

| 格式 | 说明 |
| --- | --- |
| `.ogg` | 最稳，用 Minecraft 自带的解码器 |
| `.mp3` | 已内置解码器，直接能放 |
| `.wav` / `.aiff` / `.au` | 用 Java 自带解码器 |

加完歌后，在游戏里敲一下指令让它重新扫描：

```
/musiclooper reload
```

**3. 让原版音乐闭嘴**

如果你只想听自己的歌，打开配置文件：

```
.minecraft/config/musiclooper-client.toml
```

把这里改成 `true`：

```toml
[vanilla]
    # 第三方音乐够多时，完全禁用原版音乐
    disableVanillaMusicWhenEnoughCustom = true

    # 达到几首才算"够多"（默认 5 首）
    customMusicThreshold = 5
```

只要你的音乐数量达到门槛，原版音乐就会彻底停下。

---

## 指令

| 指令 | 作用 |
| --- | --- |
| `/musiclooper reload` | 重新扫描音乐文件夹 |
| `/musiclooper status` | 查看认出几首、当前配置 |

---

## 安装

1. 装好 **Fabric Loader 0.19.5**（Minecraft 1.20.1）
2. 把 **Fabric API** 放进 `.minecraft/mods/`
3. 把 `musiclooper-fabric-1.0.0.jar` 也放进 `.minecraft/mods/`
4. 启动游戏

---

## 从源码构建

正常做法是用 Fabric Loom：

```bash
./gradlew build
```

> ⚠️ 本仓库里的 jar **不是**用 Loom 构建的。构建它的设备是 Android/Termux，
> Loom 的流水线在那里跑不通，所以走的是 `build.sh` 里的手工流程：
> 用 JDK 17 编译 → 用 FART 分两步重映射成 intermediary → 打包。
> 见下面的「实现说明」。

---

## 实现说明

### 目录结构

```
src/main/java/com/arin/musiclooper/
├── MusicLooper.java            客户端入口（ClientModInitializer）
├── Config.java                 自己读写 TOML 配置
├── client/
│   ├── ClientBootstrap.java    接 Fabric 的 tick / 指令回调
│   ├── ClientCommands.java     /musiclooper reload | status
│   ├── CustomMusicLibrary.java 音乐文件夹的创建与扫描
│   ├── CustomMusicPlayer.java  解码线程 + 渲染线程推流
│   ├── MusicController.java    音乐调度核心
│   ├── VanillaMusicProvider.java 复刻原版取曲逻辑
│   ├── mixin/
│   │   └── SoundManagerMixin.java  拦下不该播的原版音乐
│   └── audio/                  解码器与 OpenAL 输出
└── ...
stubs/                          Fabric API 的编译桩（不进 jar）
```

### 三个关键点

**1. 为什么用 Mixin**

Forge 那边有 `PlaySoundEvent` 可以直接取消一首音乐的播放，Fabric 没有等价事件。
所以挂在 `SoundManager.play` 的入口上，判定为不该播就直接 cancel。

注意 `SoundManagerMixin` 里的注解字符串写的是 **intermediary 名**
（`net.minecraft.class_1144` / `method_4873(...)`）：Mixin 注解里的字符串不会被
重映射，所以必须直接写成运行时用的名字。源码里的 Java 类型则照常用 mojmap。

**2. 为什么要分两步重映射**

正确做法是 `mojmap → obf → intermediary`，不能一步到位。

原因是 intermediary 会把某些方法登记在**接口**名下。例如 `blockPosition()`
被记在 `class_5568` 上，而 Entity 实现了它。一步式的合成映射不做继承传播，
就会生成 `di` 这种 obf 名而不是 `method_24515`，运行时直接 `NoSuchMethodError`。
FART 在第二步会自动做继承传播，结果与真实运行时一致。

**3. 为什么第三方音乐不走 `javax.sound.sampled` 播放**

手机启动器自带的 JRE 里没有 `libjsound.so`，JavaSound 只剩"读文件"的能力，
`AudioSystem.getSourceDataLine(...)` 必然失败。游戏自己的声音走 OpenAL，
所以这里复用同一个 OpenAL 上下文——而且所有 AL 调用都留在**渲染线程**：
Minecraft 初始化声音引擎时就是在渲染线程上 `alcMakeContextCurrent` 的。

另外 mp3 不走 `AudioSystem` 打开，而是直接 `new MpegAudioFileReader()`：
JavaSound 的解码器是「`META-INF/services` + 线程上下文类加载器」注册的，
Forge/Fabric 里模组 jar 不一定在那个类加载器上，会误报
`UnsupportedAudioFileException`。

---

## 许可

GPL-3.0 · 作者：**hemjj,蓝色大肥鱼**
