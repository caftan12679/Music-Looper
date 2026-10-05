package com.arin.musiclooper.client.audio;

import com.arin.musiclooper.MusicLooper;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 决定一个文件能不能播放，以及用什么解码器打开它。
 *
 * <p>支持情况：</p>
 * <ul>
 *     <li>.ogg —— Minecraft 自带的 Vorbis 解码器，永远可用；</li>
 *     <li>.mp3 —— 模组内置的 mp3spi（<b>直接实例化</b>，不走 JavaSound 的服务注册表）；</li>
 *     <li>.wav / .aiff / .au —— Java 自带解码器（这些 provider 一定注册着）。</li>
 * </ul>
 *
 * <p>为什么要区分 mp3：JavaSound 的 provider 是「META-INF/services + 线程上下文类加载器」
 * 注册的，Forge 里模组 jar 不一定在那个类加载器上，所以 {@code AudioSystem} 会误报
 * “不支持的格式”。直接 new 解码器就没有这个问题。</p>
 */
public final class AudioDecoders
{
    /**
     * 注意顺序：{@code MP3_DECODER_PRESENT} 必须排在 {@code JAVA_SOUND_EXTENSIONS} 前面。
     * 后者初始化时会读前者，顺序反了就会读到默认值 false，
     * 结果 "mp3" 永远进不了扩展名白名单——所有 mp3 都会被当成不认识的文件跳过。
     */
    private static final boolean MP3_DECODER_PRESENT = isClassPresent("javazoom.spi.mpeg.sampled.file.MpegAudioFileReader");
    private static final Set<String> JAVA_SOUND_EXTENSIONS = detectJavaSoundExtensions();
    private static final Map<Path, CachedResult> CACHE = new LinkedHashMap<>();
    private static final Set<Path> REPORTED = new LinkedHashSet<>();
    /** 日志里最多列出多少个被跳过的文件，避免刷屏 */
    private static final int MAX_REPORTS = 20;

    private AudioDecoders()
    {
    }

    private static Set<String> detectJavaSoundExtensions()
    {
        Set<String> extensions = new LinkedHashSet<>();
        try
        {
            for (AudioFileFormat.Type type : AudioSystem.getAudioFileTypes())
            {
                String extension = type.getExtension();
                if (extension != null && !extension.isEmpty())
                {
                    extensions.add(extension.toLowerCase(Locale.ROOT));
                }
            }
        }
        catch (Throwable throwable)
        {
            // JavaSound 不可用时至少还能播 ogg
        }
        // mp3spi 的 reader 不会出现在 getAudioFileTypes() 里，但它确实能解码
        if (MP3_DECODER_PRESENT)
        {
            extensions.add("mp3");
        }
        return extensions;
    }

    private static boolean isClassPresent(String className)
    {
        try
        {
            Class.forName(className, false, AudioDecoders.class.getClassLoader());
            return true;
        }
        catch (Throwable throwable)
        {
            return false;
        }
    }

    public static String extensionOf(String fileName)
    {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isOgg(String fileName)
    {
        String extension = extensionOf(fileName);
        return "ogg".equals(extension) || "oga".equals(extension);
    }

    public static boolean isMp3(String fileName)
    {
        return "mp3".equals(extensionOf(fileName));
    }

    /** 扩展名看起来像音频文件（真正的验证交给 {@link #canDecode(Path)}） */
    public static boolean hasKnownExtension(String fileName)
    {
        return isOgg(fileName) || JAVA_SOUND_EXTENSIONS.contains(extensionOf(fileName));
    }

    /** 真的能解码吗？结果会按“路径 + 大小 + 修改时间”缓存 */
    public static boolean canDecode(Path file)
    {
        String name = file.getFileName().toString();
        if (!hasKnownExtension(name))
        {
            return false;
        }

        if (isOgg(name))
        {
            return true;
        }

        long size;
        long modified;
        try
        {
            size = Files.size(file);
            modified = Files.getLastModifiedTime(file).toMillis();
        }
        catch (IOException exception)
        {
            return false;
        }

        CachedResult cached = CACHE.get(file);
        if (cached != null && cached.size == size && cached.modified == modified)
        {
            return cached.playable;
        }

        boolean playable;
        try
        {
            if (isMp3(name) && MP3_DECODER_PRESENT)
            {
                // 只用内置解码器读一下文件头，确认真的能解
                AudioInputStream probe = new javazoom.spi.mpeg.sampled.file.MpegAudioFileReader()
                        .getAudioInputStream(file.toFile());
                probe.close();
                playable = true;
            }
            else
            {
                AudioSystem.getAudioFileFormat(file.toFile());
                playable = true;
            }
        }
        catch (Throwable throwable)
        {
            playable = false;
            reportSkipped(file, throwable);
        }

        if (CACHE.size() > 4096)
        {
            CACHE.clear();
        }
        CACHE.put(file, new CachedResult(size, modified, playable));
        return playable;
    }

    private static void reportSkipped(Path file, Throwable throwable)
    {
        if (REPORTED.size() >= MAX_REPORTS || !REPORTED.add(file))
        {
            return;
        }
        MusicLooper.LOGGER.warn("跳过无法解码的文件：{}（{}）", file.getFileName(), throwable.toString());
    }

    public static PcmSource open(Path file) throws Exception
    {
        String name = file.getFileName().toString();
        if (isOgg(name))
        {
            return new OggPcmSource(file);
        }
        if (isMp3(name) && MP3_DECODER_PRESENT)
        {
            return new Mp3PcmSource(file);
        }
        return new JavaSoundPcmSource(file);
    }

    /** 给日志和说明文件用的一句话 */
    public static String describeSupportedFormats()
    {
        Set<String> list = new LinkedHashSet<>();
        list.add("ogg");
        list.addAll(JAVA_SOUND_EXTENSIONS);
        if (MP3_DECODER_PRESENT)
        {
            list.add("mp3");
        }
        return String.join(", ", list);
    }

    private record CachedResult(long size, long modified, boolean playable)
    {
    }
}
