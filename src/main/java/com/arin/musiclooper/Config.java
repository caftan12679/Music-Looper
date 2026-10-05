package com.arin.musiclooper;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * config/musiclooper-client.toml 里的配置项。
 */
@Mod.EventBusSubscriber(modid = MusicLooper.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config
{
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue LOOP_VANILLA_MUSIC;
    private static final ForgeConfigSpec.BooleanValue PLAY_CUSTOM_MUSIC;
    private static final ForgeConfigSpec.DoubleValue CUSTOM_MUSIC_VOLUME;
    private static final ForgeConfigSpec.BooleanValue DISABLE_VANILLA_WHEN_ENOUGH_CUSTOM;
    private static final ForgeConfigSpec.IntValue CUSTOM_MUSIC_THRESHOLD;

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

    static
    {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("原版音乐（Minecraft 自带背景音乐）相关设置").push("vanilla");
        LOOP_VANILLA_MUSIC = builder
                .comment("是否让原版音乐连续循环播放。",
                        "开启后，一首原版音乐播完会立刻接着播放下一首，不会再出现几分钟的静默空档。",
                        "默认：true")
                .define("loopVanillaMusic", true);
        DISABLE_VANILLA_WHEN_ENOUGH_CUSTOM = builder
                .comment("当第三方音乐数量达到下面的门槛时，是否完全禁用原版音乐。",
                        "开启并且第三方音乐数量达标时，只会播放 config/musiclooper/music 里的音乐。",
                        "默认：false")
                .define("disableVanillaMusicWhenEnoughCustom", false);
        CUSTOM_MUSIC_THRESHOLD = builder
                .comment("上面那个选项的触发门槛：第三方音乐至少要有多少首。",
                        "默认：5（即至少 5 首第三方音乐）")
                .defineInRange("customMusicThreshold", 5, 1, 1000);
        builder.pop();

        builder.comment("第三方音乐相关设置").push("custom");
        PLAY_CUSTOM_MUSIC = builder
                .comment("是否播放 config/musiclooper/music 文件夹里的第三方音乐。",
                        "关闭后该文件夹会被忽略，只播放（或循环）原版音乐。",
                        "默认：true")
                .define("playCustomMusic", true);
        CUSTOM_MUSIC_VOLUME = builder
                .comment("第三方音乐的音量倍率（0.0 ~ 1.0）。",
                        "实际音量 = 该倍率 × 游戏内“音乐”音量滑块。",
                        "默认：1.0")
                .defineInRange("customMusicVolume", 1.0D, 0.0D, 1.0D);
        builder.pop();

        SPEC = builder.build();
    }

    @SubscribeEvent
    static void onConfigLoad(ModConfigEvent event)
    {
        // 只处理本模组自己的配置
        if (event.getConfig().getSpec() != SPEC)
        {
            return;
        }

        loopVanillaMusic = LOOP_VANILLA_MUSIC.get();
        playCustomMusic = PLAY_CUSTOM_MUSIC.get();
        customMusicVolume = CUSTOM_MUSIC_VOLUME.get();
        disableVanillaMusicWhenEnoughCustom = DISABLE_VANILLA_WHEN_ENOUGH_CUSTOM.get();
        customMusicThreshold = CUSTOM_MUSIC_THRESHOLD.get();

        MusicLooper.LOGGER.debug("Music Looper 配置已加载：loopVanillaMusic={}, playCustomMusic={}, disableWhenEnough={} (>= {}), volume={}",
                loopVanillaMusic, playCustomMusic, disableVanillaMusicWhenEnoughCustom, customMusicThreshold, customMusicVolume);
    }
}
