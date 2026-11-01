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

**3. 按生物群系播放指定音乐**

想让某个群系固定放某几首歌？可以在配置里按群系（或群系标签）指定曲目。

**4. 战斗音乐**

把战斗时想听的曲子放进另一个文件夹：

```
.minecraft/config/musiclooper/combat/
```

这里的音乐**平时完全不播**，只有检测到战斗时才响起，战斗结束后自动停下，
交还给常规音乐。

判定「战斗中」的条件（满足任意一条即可）：

| 条件 | 说明 |
| --- | --- |
| 正在受伤 | 你被打了 |
| 刚攻击过 | 最近 5 秒内打过还活着的目标 |
| 被盯上 | 附近 16 格内有敌对生物把你设为目标 |

相关配置：

```toml
[combatMusic]
    enabled = true          # 总开关
    detectRadius = 16       # 检测敌人的半径（格）
    endDelay = 100          # 战斗结束后延迟多少 tick 才停（100 = 5 秒）
    combatTimeoutTicks = 100 # 出手后多久仍算在战斗
    volume = 1.0            # 音量倍率
    loop = true             # 战斗期间是否一首接一首地循环
```

### 淡入淡出

换曲时不会硬切——第三方音乐起播会从 0 音量淡入，停止或切换前会先淡出到 0。

```toml
[fade]
    fadeInMs = 1500     # 起播淡入时长（毫秒），0 = 不淡入
    fadeOutMs = 1200    # 停止 / 切换前淡出时长（毫秒），0 = 不淡出
```

其中「常规音乐 → 战斗音乐」和「战斗音乐 → 常规音乐」这两个切换，
都会先等前一首淡出完再起下一首，所以听起来不会突兀。

> 这条只作用于第三方音乐（`music/` 与 `combat/` 里的曲子）。
> 原版音乐由游戏自己管理，切换时游戏本身有短暂的音量衰减。

看当前状态：

```
/musiclooper combat
```

会显示是否在战斗、还剩多少 tick 缓冲、认出几首战斗曲。

**5. 让原版音乐闭嘴**

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
| `/musiclooper play <文件名>` | **立刻**停掉当前音乐，改播指定的这首 |
| `/musiclooper biome` | 查看当前群系 ID、命中了哪条规则 |
| `/musiclooper reload` | 重新扫描音乐文件夹 |
| `/musiclooper status` | 查看认出几首、当前配置 |
| `/musiclooper combat` | 查看战斗音乐状态（是否在战斗、认出几首）|

---

---

## 按生物群系播放指定音乐

想让某个群系固定放某几首歌？用这个功能。

### 快速上手

1. 把音乐放进 `config/musiclooper/music/`
2. 打开 `config/musiclooper-client.toml`
3. 找到 `[biomeMusic]` 段，把 `enabled` 改成 `true`
4. 在 `rules` 里写规则
5. 进游戏敲 `/musiclooper reload` 让它重新扫描

### 规则怎么写

每条规则的格式是 **`群系ID=文件名1,文件名2`**：

```toml
[biomeMusic]
    enabled = true

    rules = [
        # ① 具体群系：只在平原放这两首
        "minecraft:plains=calm.mp3,morning.mp3",

        # ② 群系标签：所有森林类群系共用（注意前面有 #）
        "#minecraft:is_forest=forest_theme.mp3",

        # ③ 兜底：前面都没命中时用它
        "*=fallback.mp3",
    ]

    # ONLY_CUSTOM  该群系只放你配的音乐，原版音乐不出声（默认）
    # MIX          你配的音乐和原版音乐交替播放
    mode = "ONLY_CUSTOM"
```

**匹配顺序**：具体群系 → 标签 → 兜底。

### 群系 ID 怎么写

格式是 `命名空间:群系名`，省略命名空间时默认 `minecraft`：

```toml
"minecraft:plains=..."      # 完整写法
"plains=..."                # 等价（默认 minecraft）
"biomesoplenty:lavender_field=..."   # 其它模组的群系也行
```

**不知道群系 ID 是什么？** 站到那个群系里敲：

```
/musiclooper biome
```

它会告诉你当前群系 ID、有没有命中规则、命中了哪几首。

### 用标签一次覆盖一大类群系

一个个列群系太麻烦时用标签，前面加 `#`：

```toml
"#minecraft:is_forest=forest.mp3"     # 所有森林
"#minecraft:is_taiga=taiga.mp3"       # 所有针叶林
"#minecraft:is_jungle=jungle.mp3"     # 所有丛林
"#minecraft:is_ocean=ocean.mp3"       # 所有海洋
"#minecraft:is_mountain=mountain.mp3" # 所有山地
"#minecraft:is_nether=nether.mp3"     # 下界全部
```

常用的还有 `is_beach`、`is_river`、`is_badlands`、`is_hill`、`is_savanna`、`is_overworld`。

> 标签是 Minecraft 自带的分类，好处是**游戏以后加了新群系也会自动覆盖**。

### 文件名怎么写

写文件名就行，和 `config/musiclooper/music/` 里的对应（**忽略大小写**）：

```toml
"minecraft:plains=Calm.MP3"     # 能匹配到 calm.mp3
```

放在子文件夹里的话，写文件名即可（不需要写路径）。

### 两个细节

**① 文件找不到怎么办？**
如果某条规则里的文件全部不存在或无法解码，那个群系会**回退到全部曲目随机**，不会出现"进这个群系就彻底没声音"的情况。日志里会记录下来。

**② 和其它设置冲突时谁优先？**
群系规则**优先**。也就是说，即使你开了「第三方音乐够多时禁用原版音乐」，只要当前群系命中了一条 `ONLY_CUSTOM` 规则，就按群系规则走。
