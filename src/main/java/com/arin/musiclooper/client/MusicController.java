package com.arin.musiclooper.client;

import com.arin.musiclooper.Config;
import com.arin.musiclooper.MusicLooper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.event.sound.PlaySoundEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 音乐调度核心：决定“下一首播什么”，并把原版音乐与第三方音乐排成一条连续的播放队列。
 *
 * <p>实现方式完全基于公开 API：</p>
 * <ul>
 *     <li>用 {@link PlaySoundEvent} 判断原版音乐什么时候开始、并拦下不需要的原版音乐；</li>
 *     <li>用 {@code SoundManager#isActive} 判断当前播放的原版曲目是否结束；</li>
 *     <li>第三方音乐由 {@link CustomMusicPlayer} 直接输出到系统音频设备。</li>
 * </ul>
 * 因此不需要 Mixin，也不依赖任何被混淆的内部字段。
 */
public final class MusicController
{
    /** 一首歌结束后等待多久再开始下一首（20 tick = 1 秒） */
    private static final int IDLE_TICKS_BEFORE_NEXT = 10;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final CustomMusicPlayer PLAYER = new CustomMusicPlayer();

    /** 当前由我们播放的原版曲目 */
    private static SoundInstance activeVanilla;
    /** 当前是否在播放第三方音乐 */
    private static boolean activeIsCustom;
    private static int idleTicks;
    private static boolean announced;
    /** 上一次看到的存档，用来判断“刚进入世界”，进入时才扫描一次音乐文件夹 */
    private static Object lastLevel;

    private MusicController()
    {
    }

    public static void tick(Minecraft minecraft)
    {
        if (minecraft.level != lastLevel)
        {
            // 刚进入（或离开）世界：这里才扫一次音乐文件夹，平时不轮询
            lastLevel = minecraft.level;
            if (minecraft.level != null)
            {
                CustomMusicLibrary.refresh();
            }
        }

        if (minecraft.level == null || minecraft.player == null)
        {
            // 主菜单 / 正在载入世界：交给原版处理，第三方音乐停下
            reset();
            return;
        }

        // 第三方音乐由渲染线程上的 OpenAL 输出：先把它驱动起来（暂停菜单里也照常播放）
        PLAYER.tick();

        if (minecraft.isPaused())
        {
            // 单人游戏打开暂停菜单时原版会暂停所有音频通道，
            // 此时不要做“曲目是否结束”的判断，否则会误判并一直重启音乐
            return;
        }

        float musicVolume = minecraft.options.getSoundSourceVolume(SoundSource.MUSIC);
        PLAYER.setGain((float) (Config.customMusicVolume * musicVolume));

        // 曲目结束检测
        if (activeIsCustom && !PLAYER.isBusy())
        {
            activeIsCustom = false;
            idleTicks = 0;
        }
        if (activeVanilla != null && !minecraft.getSoundManager().isActive(activeVanilla))
        {
            activeVanilla = null;
            idleTicks = 0;
        }

        if (activeIsCustom || activeVanilla != null)
        {
            idleTicks = 0;
            return;
        }

        // 现在没有任何音乐在播放：稍等片刻就接上下一首，形成“连续循环”
        if (++idleTicks < IDLE_TICKS_BEFORE_NEXT)
        {
            return;
        }
        idleTicks = 0;
        startNext(minecraft, musicVolume);
    }

    private static void startNext(Minecraft minecraft, float musicVolume)
    {
        List<Path> tracks = Config.playCustomMusic ? CustomMusicLibrary.playableTracks() : List.of();
        boolean enoughCustom = Config.disableVanillaMusicWhenEnoughCustom
                && tracks.size() >= Config.customMusicThreshold;
        boolean canPlayCustom = !tracks.isEmpty() && musicVolume > 0.0F;

        if (enoughCustom)
        {
            // 第三方音乐足够多，原版音乐被完全禁用
            if (canPlayCustom)
            {
                startCustom(tracks);
            }
            return;
        }

        if (!Config.loopVanillaMusic)
        {
            // 没有开启“原版循环”：原版保持自己的节奏，我们只填它留下的空档
            if (canPlayCustom)
            {
                startCustom(tracks);
            }
            return;
        }

        // 原版循环 + 有第三方音乐：随机二选一，两种音乐交替出现
        if (canPlayCustom && RANDOM.nextBoolean())
        {
            startCustom(tracks);
            return;
        }
        startVanilla(minecraft);
    }

    private static void startCustom(List<Path> tracks)
    {
        Path track = tracks.get(RANDOM.nextInt(tracks.size()));
        activeIsCustom = true;
        PLAYER.play(track);
        MusicLooper.LOGGER.debug("开始播放第三方音乐：{}", track.getFileName());
    }

    private static void startVanilla(Minecraft minecraft)
    {
        Optional<Music> music = VanillaMusicProvider.pick(minecraft);
        if (music.isEmpty())
        {
            return;
        }

        SoundInstance instance = SimpleSoundInstance.forMusic(music.get().getEvent().value());
        activeVanilla = instance;
        minecraft.getSoundManager().play(instance);
        announceOnce();
    }

    private static void announceOnce()
    {
        if (announced)
        {
            return;
        }
        announced = true;
        MusicLooper.LOGGER.info("连续音乐播放已启动（原版循环：{}，第三方音乐：{} 首）",
                Config.loopVanillaMusic, CustomMusicLibrary.playableCount());
    }

    /**
     * 拦下“不该出现”的原版音乐：
     * 第三方音乐播放期间、或者开启了“第三方音乐足够多时禁用原版”时，
     * 原版音乐管理器偷偷起的曲目会被直接丢掉，避免两首歌同时响。
     */
    public static void onPlaySound(PlaySoundEvent event)
    {
        SoundInstance instance = event.getSound();
        if (instance == null || instance.getSource() != SoundSource.MUSIC)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null)
        {
            // 主菜单和载入界面不插手
            return;
        }

        if (instance == activeVanilla)
        {
            // 我们自己的原版曲目，放行
            return;
        }

        if (ownsRotation())
        {
            event.setSound(null);
        }
    }

    private static boolean ownsRotation()
    {
        if (activeIsCustom || PLAYER.isBusy())
        {
            return true;
        }
        if (Config.disableVanillaMusicWhenEnoughCustom
                && CustomMusicLibrary.playableCount() >= Config.customMusicThreshold)
        {
            return true;
        }
        // 开了“原版循环”就由本模组统一安排曲目顺序
        return Config.loopVanillaMusic;
    }

    private static void reset()
    {
        if (activeIsCustom || PLAYER.isBusy())
        {
            PLAYER.stop();
        }
        activeIsCustom = false;
        activeVanilla = null;
        idleTicks = 0;
        lastLevel = null;
    }
}
