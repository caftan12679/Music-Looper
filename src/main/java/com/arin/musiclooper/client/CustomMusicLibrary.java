package com.arin.musiclooper.client;

import com.arin.musiclooper.MusicLooper;
import com.arin.musiclooper.client.audio.AudioDecoders;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 负责 config/musiclooper/music 文件夹的创建与扫描。
 */
public final class CustomMusicLibrary
{
    /** config/musiclooper/music */
    public static final Path MUSIC_DIR = FabricLoader.getInstance().getConfigDir()
            .resolve(MusicLooper.MODID).resolve("music");

    /** config/musiclooper/combat —— 战斗音乐，平时不参与轮播 */
    public static final Path COMBAT_DIR = FabricLoader.getInstance().getConfigDir().resolve(MusicLooper.MODID).resolve("combat");

    private static final String README_NAME = "说明.txt";

    private static volatile List<Path> tracks = List.of();
    private static final Set<Path> failedTracks = new HashSet<>();

    /** 战斗曲目（与 tracks 分开存放，常规轮播不会取到） */
    private static volatile List<Path> combatTracks = List.of();
    private static final Set<Path> failedCombatTracks = new HashSet<>();

    private CustomMusicLibrary()
    {
    }

    /** 创建文件夹并放一份说明文件 */
    public static void ensureFolder()
    {
        ensureFolder(MUSIC_DIR, readmeText());
        ensureFolder(COMBAT_DIR, combatReadmeText());
    }

    /** 创建指定目录并写入说明文件（已存在则不动） */
    private static void ensureFolder(Path dir, String readme)
    {
        try
        {
            Files.createDirectories(dir);
            Path file = dir.resolve(README_NAME);
            if (!Files.exists(file))
            {
                Files.writeString(file, readme, StandardCharsets.UTF_8);
            }
        }
        catch (IOException exception)
        {
            MusicLooper.LOGGER.warn("无法创建音乐文件夹 {}：{}", dir, exception.toString());
        }
    }

    private static String combatReadmeText()
    {
        return """
                Music Looper —— 战斗音乐文件夹
                ===============================

                把战斗时想听的音乐放进这个文件夹（也可以放子文件夹）。

                和隔壁 music 文件夹不同：这里的曲子平时完全不会播放，
                只有在检测到战斗时才会响；战斗结束后会自动停下，
                并交还给常规音乐。

                当前识别到的格式：%s

                判定「战斗中」的条件（满足任意一条即可）：
                  · 你正在受伤
                  · 你刚攻击过某个还活着的目标（默认 5 秒内算数）
                  · 附近有敌对生物把你锁定为目标

                相关配置都在 config/musiclooper-client.toml 的 [combatMusic] 段：
                  enabled        总开关（默认 true）
                  detectRadius   检测敌人的半径（默认 16 格）
                  endDelay       战斗结束后延迟多少 tick 才停（默认 100 tick = 5 秒）
                  loop           战斗期间是否一首接一首地循环（默认 true）
                  volume         音量倍率（默认 1.0）

                添加 / 删除文件后执行 /musiclooper reload 即可生效，
                用 /musiclooper combat 可以查看当前是否在战斗、认出几首。
                """.formatted(AudioDecoders.describeSupportedFormats());
    }

    private static String readmeText()
    {
        return """
                Music Looper —— 第三方音乐文件夹
                =================================

                把你自己喜欢的音乐文件放进这个文件夹（也可以放子文件夹），
                游戏里就会把它们加入随机播放列表。

                当前识别到的格式：%s

                · 支持的格式以实际运行结果为准；上面这份清单会在游戏启动时写入日志。
                · 添加 / 删除文件后执行一次指令 `/musiclooper reload` 即可生效，
                  （用 `/musiclooper status` 可以看当前认出几首）。
                · 音量跟随游戏内的“音乐”音量滑块，另外可以在
                  config/musiclooper-client.toml 里用 customMusicVolume 微调。
                · 想让原版音乐在有足够多第三方音乐时完全停下，把
                  config/musiclooper-client.toml 里的
                  disableVanillaMusicWhenEnoughCustom 改成 true
                  （门槛见 customMusicThreshold，默认 5 首）。

                文件名建议用英文 / 数字，避免在某些系统上出现编码问题。
                """.formatted(AudioDecoders.describeSupportedFormats());
    }

    /**
     * 重新扫描音乐文件夹。
     *
     * <p>扫描是纯 I/O 操作，所以<b>不</b>做定时轮询：只在进入世界时跑一次，
     * 之后由 {@code /musiclooper reload} 指令手动触发。</p>
     */
    public static void refresh()
    {
        ensureFolder();

        tracks = List.copyOf(scan(MUSIC_DIR, "第三方音乐"));
        combatTracks = List.copyOf(scan(COMBAT_DIR, "战斗音乐"));

        for (Path path : tracks)
        {
            MusicLooper.LOGGER.debug("  可播放：{}", path.getFileName());
        }
        for (Path path : combatTracks)
        {
            MusicLooper.LOGGER.debug("  战斗曲可播放：{}", path.getFileName());
        }
    }

    /** 扫描一个目录，返回可解码的音频文件（按路径排序） */
    private static List<Path> scan(Path dir, String label)
    {
        List<Path> found = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> stream = Files.walk(dir, 5))
        {
            List<Path> all = stream.filter(Files::isRegularFile).toList();
            scanned = all.size();
            for (Path path : all)
            {
                if (AudioDecoders.canDecode(path))
                {
                    found.add(path);
                }
            }
        }
        catch (IOException exception)
        {
            MusicLooper.LOGGER.warn("扫描{}文件夹失败：{}", label, exception.toString());
            return List.of();
        }

        found.sort(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)));
        MusicLooper.LOGGER.info("已扫描{}文件夹：发现 {} 个文件，其中 {} 个可播放", label, scanned, found.size());
        return found;
    }

    /** 扫描到的文件总数（含解码失败的） */
    public static int knownCount()
    {
        return tracks.size();
    }

    /** 可用的第三方音乐（排除本局游戏里播放失败的文件） */
    public static List<Path> playableTracks()
    {
        List<Path> current = tracks;
        if (failedTracks.isEmpty())
        {
            return current;
        }

        List<Path> result = new ArrayList<>(current.size());
        for (Path path : current)
        {
            if (!failedTracks.contains(path))
            {
                result.add(path);
            }
        }
        return result;
    }

    public static int playableCount()
    {
        return playableTracks().size();
    }

    public static void markFailed(Path path)
    {
        failedTracks.add(path);
    }

    // ------------------------------------------------------------------
    // 战斗音乐
    // ------------------------------------------------------------------

    /** 可用的战斗曲目（排除本局游戏里播放失败的文件） */
    public static List<Path> combatPlayableTracks()
    {
        List<Path> current = combatTracks;
        if (failedCombatTracks.isEmpty())
        {
            return current;
        }

        List<Path> result = new ArrayList<>(current.size());
        for (Path path : current)
        {
            if (!failedCombatTracks.contains(path))
            {
                result.add(path);
            }
        }
        return result;
    }

    public static int combatPlayableCount()
    {
        return combatPlayableTracks().size();
    }

    public static void markCombatFailed(Path path)
    {
        failedCombatTracks.add(path);
    }
}
