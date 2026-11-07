package com.musiclooper.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;

import java.util.Optional;

/**
 * 挑选“下一首原版音乐”。
 *
 * <p><b>26.1.2 的关键变化：</b>原版把背景音乐从 {@code Biome} 挪到了新的
 * 「环境属性」系统（{@code EnvironmentAttributes.BACKGROUND_MUSIC}），
 * {@code Biome.getBackgroundMusic()} 这个老方法已经删掉了。</p>
 *
 * <p>好消息是 {@code Minecraft.getSituationalMusic()} 还在，而且它内部已经把
 * 维度、是否创造模式、是否水下、群系自带音乐、以及最后的 {@code Musics.GAME}
 * 兜底全部处理好了。所以这里不再自己实现那套逻辑，直接问原版要答案就行。</p>
 *
 * <p>对比 1.21.1 版：那一版需要手工复刻原版的选曲流程，还要自己补
 * {@code orElse(Musics.GAME)} 兜底（1.1.0-hotfix 修的就是这个）。
 * 现在这两件事都由原版代劳，代码因此短了很多，也更不容易跟原版跑偏。</p>
 */
public final class VanillaMusicProvider
{
    private VanillaMusicProvider()
    {
    }

    /**
     * 挑一首“此刻该放的原版音乐”。
     *
     * @return 选中的曲目；永远不是空（最差也会兜底到 {@link Musics#GAME}）
     */
    public static Optional<Music> pick(Minecraft minecraft)
    {
        if (minecraft == null || minecraft.level == null || minecraft.player == null)
        {
            // 主菜单 / 载入中：不插手，交给原版音乐管理器
            return Optional.empty();
        }

        Music music;
        try
        {
            music = minecraft.getSituationalMusic();
        }
        catch (Throwable ignored)
        {
            // 万一原版内部因为某个维度数据不全抛异常，也不要让整个调度挂掉
            music = null;
        }

        // 原版在某些分支（比如玩家数据还没同步完）会返回 null，
        // 这里补一个通用的“游戏背景音乐”兜底，避免出现「谁都没在放」的静音。
        return Optional.of(music != null ? music : Musics.GAME);
    }

    /**
     * 取当前群系（给 {@link BiomeMusicRules} 用的）。
     *
     * <p>26.1.2 里 {@code getBiome(BlockPos)} 仍然是 {@code LevelReader} 的默认方法，
     * 所以这里照旧可用。</p>
     *
     * @return 当前群系；玩家或世界为空时返回 {@code null}
     */
    public static Holder<Biome> currentBiome(Minecraft minecraft)
    {
        if (minecraft == null || minecraft.level == null || minecraft.player == null)
        {
            return null;
        }
        return minecraft.level.getBiome(minecraft.player.blockPosition());
    }

    /**
     * 玩家当前是否在「会触发原版特殊音乐」的状态下。
     *
     * <p>纯辅助方法，给调试指令用；不做逻辑判断。</p>
     */
    public static String describeState(Player player, Level level)
    {
        if (player == null || level == null)
        {
            return "(无世界)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(level.dimension().identifier());
        if (player.isCreative())
        {
            sb.append(" · 创造模式");
        }
        if (player.isUnderWater())
        {
            sb.append(" · 水下");
        }
        return sb.toString();
    }
}
