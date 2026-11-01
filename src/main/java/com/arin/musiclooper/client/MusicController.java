package com.arin.musiclooper.client;

import com.arin.musiclooper.Config;
import com.arin.musiclooper.MusicLooper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 音乐调度核心：决定“下一首播什么”，并把原版音乐与第三方音乐排成一条连续的播放队列。
 *
 * <p>实现方式完全基于公开 API + NeoForge 事件：用 {@link PlaySoundEvent}
 * 拦下不需要的原版音乐，用 {@code SoundManager#isActive} 判断曲目是否结束。</p>
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
    /** 当前是否在播放战斗音乐 */
    private static boolean activeIsCombat;
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

        // 战斗音乐优先于一切常规安排：检测到战斗时接管播放，战斗结束后再交还
        if (tickCombat(minecraft, musicVolume))
        {
            return;
        }

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

    /**
     * 战斗音乐分支。每 tick 在常规安排之前调用一次。
     *
     * <p>返回 {@code true} 表示本 tick 由战斗音乐接管，调用方应当直接结束本次 tick，
     * 不要再走原版 / 第三方音乐的轮播逻辑。</p>
     */
    private static boolean tickCombat(Minecraft minecraft, float musicVolume)
    {
        boolean inCombat = CombatTracker.update(minecraft.player);

        if (inCombat)
        {
            List<Path> combatTracks = CustomMusicLibrary.combatPlayableTracks();
            if (!Config.combatMusicEnabled || combatTracks.isEmpty())
            {
                // 没配战斗音乐就当没这个功能，常规音乐照常播
                if (activeIsCombat)
                {
                    PLAYER.stop();
                    activeIsCombat = false;
                    idleTicks = 0;
                }
                return false;
            }

            PLAYER.setGain((float) (Config.combatMusicVolume * musicVolume));

            if (!activeIsCombat)
            {
                // 刚进入战斗：先淡出正在播的音乐，再换战斗曲
                activeIsCombat = true;
                if (PLAYER.isBusy())
                {
                    // 停掉原版（它有自己的短淡出），自定义曲交给 fadeOutThen 平滑收尾
                    stopVanillaOnly(minecraft);
                    PLAYER.fadeOutThen(() -> startCombat(combatTracks));
                }
                else
                {
                    stopCurrent(minecraft);
                    startCombat(combatTracks);
                }
            }
            else if (!PLAYER.isBusy())
            {
                // 一首战斗曲放完了，战斗还没结束
                if (Config.combatMusicLoop)
                {
                    startCombat(combatTracks);
                }
                else
                {
                    activeIsCombat = false;
                }
            }
            return true;
        }

        // 战斗结束：淡出战斗曲，淡完再把播放权交还给常规逻辑
        if (activeIsCombat)
        {
            activeIsCombat = false;
            PLAYER.fadeOutThen(() ->
            {
                PLAYER.stop();
                idleTicks = 0;
            });
        }
        return false;
    }

    /** 随机挑一首战斗曲播放 */
    private static void startCombat(List<Path> combatTracks)
    {
        Path track = combatTracks.get(RANDOM.nextInt(combatTracks.size()));
        activeIsCombat = true;
        PLAYER.play(track);
        MusicLooper.LOGGER.debug("开始播放战斗音乐：{}", track.getFileName());
    }

    private static void startNext(Minecraft minecraft, float musicVolume)
    {
        List<Path> allTracks = Config.playCustomMusic ? CustomMusicLibrary.playableTracks() : List.of();
        boolean canPlayCustom = !allTracks.isEmpty() && musicVolume > 0.0F;

        // 先看当前生物群系有没有专门配置的曲目。命中时用命中结果替代全部曲目。
        List<Path> biomeTracks = pickBiomeTracks(minecraft, allTracks);
        List<Path> tracks = biomeTracks != null ? biomeTracks : allTracks;

        boolean enoughCustom = Config.disableVanillaMusicWhenEnoughCustom
                && allTracks.size() >= Config.customMusicThreshold;

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

        // 命中的群系如果设成 ONLY_CUSTOM，就让本模组接管，原版音乐不再插进来
        if (biomeTracks != null && BiomeMusicRules.blocksVanilla(biomeOf(minecraft), allTracks))
        {
            startCustom(biomeTracks);
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

    /** 当前生物群系（玩家为空时也返回 null） */
    private static Holder<Biome> biomeOf(Minecraft minecraft)
    {
        if (minecraft.level == null || minecraft.player == null)
        {
            return null;
        }
        return minecraft.level.getBiome(minecraft.player.blockPosition());
    }

    /** 命中的群系曲目；未命中返回 null */
    private static List<Path> pickBiomeTracks(Minecraft minecraft, List<Path> available)
    {
        if (!BiomeMusicRules.isEnabled())
        {
            return null;
        }
        return BiomeMusicRules.pick(biomeOf(minecraft), available);
    }

    private static void startCustom(List<Path> tracks)
    {
        Path track = tracks.get(RANDOM.nextInt(tracks.size()));
        activeIsCustom = true;
        PLAYER.play(track);
        MusicLooper.LOGGER.debug("开始播放第三方音乐：{}", track.getFileName());
    }

    /**
     * 立刻停掉当前音乐（原版和第三方都停），改播指定文件。
     *
     * <p>供 {@code /musiclooper play <文件名>} 使用。暂停菜单里也能用 ——
     * 反正我们本来就只在 tick 里驱动播放器，不依赖原版音乐的节奏。</p>
     *
     * @return 实际播放的文件；文件找不到或不可播放时返回 {@code null}
     */
    public static Path playNow(Minecraft minecraft, String fileName)
    {
        Path target = findTrack(fileName);
        if (target == null)
        {
            return null;
        }

        // 停掉我们自己的原版曲目和第三方音乐
        stopCurrent(minecraft);

        activeIsCustom = true;
        PLAYER.play(target);
        MusicLooper.LOGGER.info("手动切歌：{}", target.getFileName());
        return target;
    }

    /** 按文件名（或文件名的一部分）在当前可用曲目里找一首 */
    public static Path findTrack(String query)
    {
        if (query == null || query.isBlank())
        {
            return null;
        }

        List<Path> tracks = CustomMusicLibrary.playableTracks();
        String wanted = query.trim();

        // 先精确匹配文件名（忽略大小写），再退化成「包含」匹配
        for (Path path : tracks)
        {
            if (path.getFileName().toString().equalsIgnoreCase(wanted))
            {
                return path;
            }
        }

        String lower = wanted.toLowerCase(java.util.Locale.ROOT);
        for (Path path : tracks)
        {
            if (path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).contains(lower))
            {
                return path;
            }
        }
        return null;
    }

    /** 只停原版曲目，不动第三方音乐（给淡出切换用） */
    private static void stopVanillaOnly(Minecraft minecraft)
    {
        if (activeVanilla != null && minecraft != null)
        {
            try
            {
                minecraft.getSoundManager().stop(activeVanilla);
            }
            catch (Throwable ignored)
            {
                // 音乐引擎可能还没就绪，忽略
            }
        }
        activeVanilla = null;
    }

    /** 停掉当前正在播的音乐（原版 + 第三方） */
    private static void stopCurrent(Minecraft minecraft)
    {
        if (activeVanilla != null && minecraft != null)
        {
            try
            {
                minecraft.getSoundManager().stop(activeVanilla);
            }
            catch (Throwable ignored)
            {
                // 音乐引擎可能还没就绪，忽略
            }
        }
        activeVanilla = null;

        if (activeIsCustom || activeIsCombat || PLAYER.isBusy())
        {
            PLAYER.stop();
        }
        activeIsCustom = false;
        activeIsCombat = false;
        idleTicks = 0;
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
     * 这次 {@code SoundManager.play} 要不要拦下来？
     *
     * <p>由 {@code SoundManagerMixin} 在方法入口处调用。判定规则与 Forge 版一致：</p>
     * <ul>
     *     <li>不是音乐通道 → 放行（音效照常）；</li>
     *     <li>主菜单 / 载入界面 → 放行（不插手）；</li>
     *     <li>是我们自己挑的原版曲目 → 放行；</li>
     *     <li>其余情况由 {@link #ownsRotation()} 决定。</li>
     * </ul>
     */
    public static void onPlaySound(PlaySoundEvent event)
    {
        SoundInstance sound = event.getSound();
        if (sound == null || sound.getSource() != SoundSource.MUSIC)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null)
        {
            return;
        }

        if (sound == activeVanilla)
        {
            return;
        }

        if (ownsRotation())
        {
            event.setSound(null);
        }
    }

    private static boolean ownsRotation()
    {
        if (activeIsCustom || activeIsCombat || PLAYER.isFading() || PLAYER.isBusy())
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
        if (activeIsCustom || activeIsCombat || PLAYER.isBusy())
        {
            PLAYER.stop();
        }
        activeIsCustom = false;
        activeIsCombat = false;
        activeVanilla = null;
        idleTicks = 0;
        lastLevel = null;
        CombatTracker.reset();
    }
}
