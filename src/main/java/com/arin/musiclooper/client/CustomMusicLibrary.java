package com.arin.musiclooper.client;

import com.arin.musiclooper.MusicLooper;
import com.arin.musiclooper.client.audio.AudioDecoders;
import net.minecraftforge.fml.loading.FMLPaths;

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
    public static final Path MUSIC_DIR = FMLPaths.CONFIGDIR.get().resolve(MusicLooper.MODID).resolve("music");

    private static final String README_NAME = "说明.txt";

    private static volatile List<Path> tracks = List.of();
    private static final Set<Path> failedTracks = new HashSet<>();

    private CustomMusicLibrary()
    {
    }

    /** 创建文件夹并放一份说明文件 */
    public static void ensureFolder()
    {
        try
        {
            Files.createDirectories(MUSIC_DIR);
            Path readme = MUSIC_DIR.resolve(README_NAME);
            if (!Files.exists(readme))
            {
                Files.writeString(readme, readmeText(), StandardCharsets.UTF_8);
            }
        }
        catch (IOException exception)
        {
            MusicLooper.LOGGER.warn("无法创建第三方音乐文件夹 {}：{}", MUSIC_DIR, exception.toString());
        }
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

        List<Path> found = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> stream = Files.walk(MUSIC_DIR, 5))
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
            MusicLooper.LOGGER.warn("扫描第三方音乐文件夹失败：{}", exception.toString());
            return;
        }

        found.sort(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)));
        MusicLooper.LOGGER.info("已扫描音乐文件夹：发现 {} 个文件，其中 {} 个可播放", scanned, found.size());

        List<Path> previous = tracks;
        if (!found.equals(previous))
        {
            tracks = List.copyOf(found);
            for (Path path : found)
            {
                MusicLooper.LOGGER.debug("  可播放：{}", path.getFileName());
            }
        }
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
}
