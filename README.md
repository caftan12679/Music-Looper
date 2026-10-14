# Music Looper（NeoForge 1.21.1 版）

让 Minecraft 的音乐**永远不停**，还能放你自己的歌。

> 适用版本：Minecraft **1.21.1** + NeoForge **21.1.259** · 纯客户端模组
> 服务器不用装。需要 **Java 21**

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

1. 装好 **NeoForge 21.1.259**（Minecraft 1.21.1）
2. 把 `musiclooper-neoforge-1.21.1-1.0.0.jar` 放进 `.minecraft/mods/`
3. 启动游戏

---

## 与另外三个版本的差别

这是第四个版本（Forge 1.20.1 / Fabric 1.20.1 / Fabric 1.21.1 / NeoForge 1.21.1）。
NeoForge 这一版是**最省事的**：

| | Forge 1.20.1 | Fabric | **NeoForge 1.21.1** |
| --- | --- | --- | --- |
| 运行时命名空间 | mojmap 类名 + SRG 成员名 | intermediary | **mojmap** |
| 需要重映射 | 需要（FART） | 需要（两步 FART） | **不需要** |
| 拦原版音乐 | `PlaySoundEvent` | 得用 Mixin | **`PlaySoundEvent`** |

**① 不需要重映射**：NeoForge 1.21.1 运行时就是 Mojang 官方名。
实测 NeoForge universal jar 里 **118 个类**引用 `net/minecraft/client/...`，
**0 个**引用 SRG 名（`m_xxxxx_`）。所以编译产物即为成品。

**② 不需要 Mixin**：NeoForge 提供了
`net.neoforged.neoforge.client.event.sound.PlaySoundEvent`，
和 Forge 版写法完全一致。（Fabric 版因为没有等价事件，才不得不用 Mixin 挂
`SoundManager.play`。）

代码复用情况：`audio/` 5 个文件 + `CustomMusicPlayer` + `VanillaMusicProvider`
**原样搬过来零改动**；`MusicController` 的调度逻辑也一行没改。

---

## 一个踩到的坑（记一下）

配置监听不能靠 `@EventBusSubscriber` 的默认值：

```java
// ✗ 错的：bus 默认是 GAME，而 ModConfigEvent 在 MOD 总线上
@EventBusSubscriber(modid = MODID)
```

`ModConfigEvent` 实现了 `IModBusEvent`，是在 **MOD 总线**上触发的；
而 `@EventBusSubscriber` 的 `bus` 属性默认值是 `GAME`
（1.21.1 起还被标记为 `@Deprecated(forRemoval = true)`）。
用默认值注册会导致**配置永远加载不上，所有配置项静默保持默认值**。

正确做法是在模组构造器里显式注册：

```java
modEventBus.addListener(Config::onConfigLoad);
```

（`ClientTickEvent.Post` 和 `PlaySoundEvent` 都在 GAME 总线上，
所以用 `NeoForge.EVENT_BUS.addListener(...)` 注册是对的。）

---

## 从源码构建

正常做法是用 NeoGradle：

```bash
./gradlew build
```

产物在 `build/libs/`。

> ⚠️ 本仓库里的 jar **不是**用 Gradle 构建的。构建它的设备是 Android/Termux，
> Gradle 在那里跑不通，所以走的是手工流程：
> 用 JDK 21 编译（classpath = 1.21.1 的 mojmap 客户端 + NeoForge universal +
> FML/bus 等依赖）→ 直接打包。**因为不需要重映射，这一步比 Forge/Fabric 版简单得多。**

编译用到的 NeoForge 依赖：

```
net.neoforged:neoforge:21.1.259:universal
net.neoforged.fancymodloader:loader:4.0.45     （FMLPaths / ModConfig / ModContainer / @Mod）
net.neoforged:bus:8.0.5                        （IEventBus / @SubscribeEvent）
net.neoforged:coremods:7.0.3
net.neoforged:mergetool:2.0.0:api              （Dist 标记注解在这里）
```

---

## 许可

GPL-3.0 · 作者：**hemjj,蓝色大肥鱼**
