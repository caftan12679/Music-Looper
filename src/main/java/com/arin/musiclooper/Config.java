package com.arin.musiclooper;

import com.arin.musiclooper.client.BiomeMusicRules;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * config/musiclooper-client.toml 里的配置项。
 *
 * <p>Fabric 没有内置的配置系统，所以这里自己读写一份极简 TOML：
 * 支持 {@code [section]} 分段和 {@code key = value} 两种写法，只认 boolean / 整数 / 小数。
 * 文件格式与 Forge 版保持一致，两个版本的用户看到的是同一个文件。</p>
 */
public final class Config
{
    /** config/musiclooper-client.toml */
    public static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("musiclooper-client.toml");

    /** 原版音乐是否连续播放（一首结束立刻接下一首，不再有几分钟的空档） */
    public static boolean loopVanillaMusic = true;
    /** 是否播放 config/musiclooper/music 里的第三方音乐 */
    public static boolean playCustomMusic = true;
    /** 第三方音乐的音量倍率，最终音量 = 该值 × 游戏内“音乐”音量滑块 */
    public static double customMusicVolume = 1.0D;
    /** 第三方音乐足够多时，是否完全禁用原版音乐 */
    public static boolean disableVanillaMusicWhenEnoughCustom = false;
    /** “足够多”的数量门槛 */
    public static int customMusicThreshold = 5;

    /** 是否启用「按生物群系播放指定音乐」 */
    public static boolean biomeMusicEnabled = false;
    /** 群系规则原文，格式：群系ID=文件名1,文件名2；支持 #标签 与 * 兜底 */
    public static List<String> biomeMusicRules = List.of();
    /** 命中群系后原版音乐怎么处理 */
    public static BiomeMusicRules.Mode biomeMusicMode = BiomeMusicRules.Mode.ONLY_CUSTOM;

    private Config()
    {
    }

    /** 读取配置；文件不存在就先写一份带注释的默认值 */
    public static void load()
    {
        try
        {
            if (!Files.exists(FILE))
            {
                Files.createDirectories(FILE.getParent());
                Files.writeString(FILE, defaultText(), StandardCharsets.UTF_8);
                MusicLooper.LOGGER.info("已生成默认配置：{}", FILE);
            }
        }
        catch (IOException exception)
        {
            MusicLooper.LOGGER.warn("无法写入默认配置 {}：{}", FILE, exception.toString());
            return;
        }

        Map<String, String> values = new HashMap<>();
        String section = "";
        // 跨行数组：遇到 key = [ 之后，把后续行攒起来直到 ]
        String pendingKey = null;
        StringBuilder pendingValue = new StringBuilder();

        try
        {
            List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
            for (String raw : lines)
            {
                String line = stripComment(raw).trim();
                if (line.isEmpty())
                {
                    continue;
                }

                // 正在收集数组内容
                if (pendingKey != null)
                {
                    pendingValue.append(line);
                    if (line.contains("]"))
                    {
                        values.put(pendingKey, pendingValue.toString());
                        pendingKey = null;
                        pendingValue.setLength(0);
                    }
                    continue;
                }

                if (line.startsWith("[") && line.endsWith("]"))
                {
                    section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.ROOT);
                    continue;
                }

                int equals = line.indexOf('=');
                if (equals < 0)
                {
                    continue;
                }

                String key = section + "." + line.substring(0, equals).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(equals + 1).trim();

                // 数组开头但没有在同一行闭合：先攒着
                if (value.startsWith("[") && !value.contains("]"))
                {
                    pendingKey = key;
                    pendingValue.setLength(0);
                    pendingValue.append(value);
                    continue;
                }

                values.put(key, value);
            }
        }
        catch (IOException exception)
        {
            MusicLooper.LOGGER.warn("读取配置失败，将使用默认值：{}", exception.toString());
            return;
        }

        loopVanillaMusic = bool(values, "vanilla.loopVanillaMusic", loopVanillaMusic);
        disableVanillaMusicWhenEnoughCustom = bool(values, "vanilla.disableVanillaMusicWhenEnoughCustom", disableVanillaMusicWhenEnoughCustom);
        customMusicThreshold = integer(values, "vanilla.customMusicThreshold", customMusicThreshold);
        playCustomMusic = bool(values, "custom.playCustomMusic", playCustomMusic);
        customMusicVolume = decimal(values, "custom.customMusicVolume", customMusicVolume);

        biomeMusicEnabled = bool(values, "biomemusic.enabled", biomeMusicEnabled);
        biomeMusicRules = array(values, "biomemusic.rules", biomeMusicRules);
        biomeMusicMode = modeOf(values.get("biomemusic.mode"), biomeMusicMode);

        // 交给匹配逻辑
        BiomeMusicRules.apply(biomeMusicEnabled, biomeMusicRules, biomeMusicMode);

        MusicLooper.LOGGER.info("配置已加载：loopVanillaMusic={}, playCustomMusic={}, disableWhenEnough={} (>= {}), volume={}",
                loopVanillaMusic, playCustomMusic, disableVanillaMusicWhenEnoughCustom, customMusicThreshold, customMusicVolume);
    }

    private static BiomeMusicRules.Mode modeOf(String raw, BiomeMusicRules.Mode fallback)
    {
        if (raw == null)
        {
            return fallback;
        }
        String value = raw.trim().replace("\"", "").replace("'", "").toUpperCase(Locale.ROOT);
        return switch (value)
        {
            case "ONLY_CUSTOM" -> BiomeMusicRules.Mode.ONLY_CUSTOM;
            case "MIX" -> BiomeMusicRules.Mode.MIX;
            default ->
            {
                MusicLooper.LOGGER.warn("未知的 biomeMusic.mode：{}，使用默认值 {}", raw, fallback);
                yield fallback;
            }
        };
    }

    /** 去掉行尾注释；TOML 里 # 之后的都是注释 */
    private static String stripComment(String line)
    {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }

    private static boolean bool(Map<String, String> values, String key, boolean fallback)
    {
        String raw = values.get(key);
        if (raw == null)
        {
            return fallback;
        }
        return "true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)
                ? Boolean.parseBoolean(raw)
                : fallback;
    }

    private static int integer(Map<String, String> values, String key, int fallback)
    {
        try
        {
            String raw = values.get(key);
            return raw == null ? fallback : Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException exception)
        {
            MusicLooper.LOGGER.warn("配置项 {} 不是合法整数，使用默认值 {}", key, fallback);
            return fallback;
        }
    }

    private static double decimal(Map<String, String> values, String key, double fallback)
    {
        try
        {
            String raw = values.get(key);
            return raw == null ? fallback : Double.parseDouble(raw.trim());
        }
        catch (NumberFormatException exception)
        {
            MusicLooper.LOGGER.warn("配置项 {} 不是合法小数，使用默认值 {}", key, fallback);
            return fallback;
        }
    }

    /**
     * 解析字符串数组，支持两种写法：
     * <pre>
     *   key = ["a", "b"]        单行
     *   key = [                 跨行
     *       "a",
     *       "b",
     *   ]
     * </pre>
     * 元素两端的引号会被去掉。
     */
    private static List<String> array(Map<String, String> values, String key, List<String> fallback)
    {
        String raw = values.get(key);
        if (raw == null)
        {
            return fallback;
        }

        String body = raw.trim();
        int open = body.indexOf('[');
        int close = body.lastIndexOf(']');
        if (open < 0 || close <= open)
        {
            MusicLooper.LOGGER.warn("配置项 {} 不是合法的数组，使用默认值", key);
            return fallback;
        }
        body = body.substring(open + 1, close);

        List<String> result = new ArrayList<>();
        for (String part : body.split(","))
        {
            String item = part.trim();
            // 去掉两端引号
            if (item.length() >= 2
                    && ((item.startsWith("\"") && item.endsWith("\""))
                        || (item.startsWith("'") && item.endsWith("'"))))
            {
                item = item.substring(1, item.length() - 1);
            }
            if (!item.isEmpty())
            {
                result.add(item);
            }
        }
        return result;
    }

    private static String defaultText()
    {
        return """
                # Music Looper 配置文件
                #
                # 修改后需要重启游戏才会生效。

                [vanilla]
                    # 是否让原版音乐连续循环播放。
                    # 开启后，一首原版音乐播完会立刻接着播放下一首，不会再出现几分钟的静默空档。
                    # 默认：true
                    loopVanillaMusic = true

                    # 当第三方音乐数量达到下面的门槛时，是否完全禁用原版音乐。
                    # 开启并且第三方音乐数量达标时，只会播放 config/musiclooper/music 里的音乐。
                    # 默认：false
                    disableVanillaMusicWhenEnoughCustom = false

                    # 上面那个选项的触发门槛：第三方音乐至少要有多少首。
                    # 默认：5（即至少 5 首第三方音乐）
                    customMusicThreshold = 5

                [custom]
                    # 是否播放 config/musiclooper/music 文件夹里的第三方音乐。
                    # 关闭后该文件夹会被忽略，只播放（或循环）原版音乐。
                    # 默认：true
                    playCustomMusic = true

                    # 第三方音乐的音量倍率（0.0 ~ 1.0）。
                    # 实际音量 = 该倍率 × 游戏内“音乐”音量滑块。
                    # 默认：1.0
                    customMusicVolume = 1.0

                # 按生物群系播放指定音乐
                # 需要先把音乐放进 config/musiclooper/music
                [biomeMusic]
                    # 总开关
                    enabled = false

                    # 规则格式：群系ID=文件名1,文件名2
                    #   具体群系：minecraft:plains（省略命名空间时默认 minecraft）
                    #   群系标签："#minecraft:is_forest"（一次覆盖所有森林类群系）
                    #   兜底规则：*（前面都没命中时使用）
                    rules = [
                        "minecraft:plains=calm.mp3,morning.mp3",
                        "#minecraft:is_forest=forest_theme.mp3",
                    ]

                    # 命中的群系里，原版音乐怎么处理：
                    #   ONLY_CUSTOM  只播配置的音乐（默认）
                    #   MIX          配置的音乐与原版交替
                    mode = "ONLY_CUSTOM"
                """;
    }
}
