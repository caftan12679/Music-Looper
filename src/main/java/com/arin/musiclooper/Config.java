package com.arin.musiclooper;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

                String key = line.substring(0, equals).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(equals + 1).trim();
                values.put(section + "." + key, value);
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

        MusicLooper.LOGGER.info("配置已加载：loopVanillaMusic={}, playCustomMusic={}, disableWhenEnough={} (>= {}), volume={}",
                loopVanillaMusic, playCustomMusic, disableVanillaMusicWhenEnoughCustom, customMusicThreshold, customMusicVolume);
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
                """;
    }
}
