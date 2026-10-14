# Music Looper（1.21.1 / Fabric 版）

让 Minecraft 的音乐**永远不停**，还能放你自己的歌。

> 适用版本：Minecraft **1.21.1** + Fabric Loader **0.19.5** · 纯客户端模组
> 需要同时安装 **Fabric API**（1.21.1 对应版本）

> 需要 **Java 21**（Minecraft 1.21 起的要求）

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
| `.ogg` | 用 Minecraft 自带的解码器（1.21.1 里是 `JOrbisAudioStream`） |
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

1. 装好 **Fabric Loader 0.19.5**（Minecraft 1.21.1）
2. 把 **Fabric API**（1.21.1 版）放进 `.minecraft/mods/`
3. 把 `musiclooper-fabric-1.21.1-1.0.0.jar` 也放进 `.minecraft/mods/`
4. 启动游戏（需要 Java 21）

---

## 与 1.20.1 版的差别

从 1.20.1 的 Fabric 版移植过来，代码几乎原样复用。实际改动只有三处：

| 改动 | 原因 |
| --- | --- |
| `OggAudioStream` → `JOrbisAudioStream` | 1.21 重命名了这个类，包名也从 `com.mojang.blaze3d.audio` 移到 `net.minecraft.client.sounds` |
| `--release 17` → `--release 21`，Mixin `JAVA_17` → `JAVA_21` | Minecraft 1.21 起要求 Java 21 |
| `fabric.mod.json` 的版本约束 | `minecraft` 改成 `~1.21.1`，`java` 改成 `>=21` |

**为什么其他都不用改**：Fabric 的 intermediary 命名空间跨版本相当稳定。
实测 20 个用到的类里，19 个的 intermediary 名字在两个版本里**指向同一个 MC 类**
（例如 `class_310` 一直是 `Minecraft`），Mixin 挂的 `method_4873` / `method_4877`
也完全一致。

唯一的例外就是那个 Ogg 解码器——顺带一提，改完名字后它的
**运行时名字没变**（都还是 `class_4228`），只有中间的 mojmap 名变了。

---

## 从源码构建

正常做法是用 Fabric Loom。但本仓库里的 jar **不是**用 Loom 构建的：
构建它的设备是 Android/Termux，Loom 在那里跑不通。走的是 `build.sh` 里的手工流程：

1. 用 JDK 21 编译（classpath = FART 反混淆后的 1.21.1 客户端 + Fabric Loader + Mixin）
2. 用 FART **分两步**重映射：`mojmap → obf → intermediary`
3. 打包（含 `fabric.mod.json`、`mixins.json`、内嵌 mp3 解码器）

### 为什么必须分两步

intermediary 会把某些方法登记在**接口**名下。例如 `blockPosition()` 记在
`class_5568` 上，而 Entity 实现了它。一步式（直接合成的 `mojmap → intermediary`）
不做继承传播，会生成 obf 名而不是 `method_XXXX`，运行时 `NoSuchMethodError`。
FART 在第二步会自动做继承传播。

---

## 许可

GPL-3.0 · 作者：**hemjj,蓝色大肥鱼**
