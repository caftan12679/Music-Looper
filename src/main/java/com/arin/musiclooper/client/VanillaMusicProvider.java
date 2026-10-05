package com.arin.musiclooper.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

/**
 * 挑选“下一首原版音乐”。
 *
 * <p>与原版音乐管理器的取曲逻辑保持一致：优先使用玩家所在生物群系自带的背景音乐，
 * 这样不同维度（主世界 / 下界 / 末地）和不同群系会自动播放对应的原版曲目。
 * 另外补上原版的特殊情形（创造模式、水下）。</p>
 */
public final class VanillaMusicProvider
{
    private VanillaMusicProvider()
    {
    }

    public static Optional<Music> pick(Minecraft minecraft)
    {
        Level level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null)
        {
            return Optional.empty();
        }

        // 末地：原版会播放末地音乐
        if (level.dimension() == Level.END && !player.isCreative())
        {
            Optional<Music> endMusic = biomeMusic(level, player);
            if (endMusic.isPresent())
            {
                return endMusic;
            }
        }

        // 创造模式：原版播放“创造模式”曲目
        if (player.isCreative() && level.dimension() == Level.OVERWORLD)
        {
            return Optional.of(new Music(SoundEvents.MUSIC_CREATIVE, 12000, 24000, false));
        }

        // 水下：原版的水下音乐
        if (player.isUnderWater())
        {
            return Optional.of(new Music(SoundEvents.MUSIC_UNDER_WATER, 12000, 24000, false));
        }

        return biomeMusic(level, player);
    }

    private static Optional<Music> biomeMusic(Level level, LocalPlayer player)
    {
        Holder<Biome> biome = level.getBiome(player.blockPosition());
        return biome.value().getBackgroundMusic();
    }
}
