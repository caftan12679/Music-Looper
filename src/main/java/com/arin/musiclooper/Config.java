package com.arin.musiclooper;

import com.arin.musiclooper.client.BiomeMusicRules;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;

import java.util.List;

/**
 * config/musiclooper-client.toml 里的配置项。
 */
public final class Config
{
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue LOOP_VANILLA_MUSIC;
    private static final ModConfigSpec.BooleanValue PLAY_CUSTOM_MUSIC;
    private static final ModConfigSpec.DoubleValue CUSTOM_MUSIC_VOLUME;
    private static final ModConfigSpec.BooleanValue DISABLE_VANILLA_WHEN_ENOUGH_CUSTOM;
    private static final ModConfigSpec.IntValue CUSTOM_MUSIC_THRESHOLD;
    private static final ModConfigSpec.BooleanValue BIOME_MUSIC_ENABLED;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_MUSIC_RULES;
    private static final ModConfigSpec.EnumValue<BiomeMusicRules.Mode> BIOME_MUSIC_MODE;
    private static final ModConfigSpec.BooleanValue COMBAT_MUSIC_ENABLED;
    private static final ModConfigSpec.IntValue COMBAT_MUSIC_DETECT_RADIUS;
    private static final ModConfigSpec.IntValue COMBAT_MUSIC_END_DELAY;
    private static final ModConfigSpec.IntValue COMBAT_MUSIC_TIMEOUT_TICKS;
    private static final ModConfigSpec.DoubleValue COMBAT_MUSIC_VOLUME;
    private static final ModConfigSpec.BooleanValue COMBAT_MUSIC_LOOP;

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
    /** 群系规则，格式：群系ID=文件名1,文件名2；支持 #标签 与 * 兜底 */
    public static List<String> biomeMusicRules = List.of();
    /** 命中群系后原版音乐怎么处理 */
    public static BiomeMusicRules.Mode biomeMusicMode = BiomeMusicRules.Mode.ONLY_CUSTOM;

    /** 是否启用战斗音乐（config/musiclooper/combat） */
    public static boolean combatMusicEnabled = true;
    /** 检测敌人的半径（格） */
    public static int combatMusicDetectRadius = 16;
    /** 战斗结束后再放多少 tick 才停（100 tick = 5 秒） */
    public static int combatMusicEndDelay = 100;
    /** 出手后多少 tick 内仍算「在战斗」 */
    public static int combatMusicTimeoutTicks = 100;
    /** 战斗音乐音量倍率 */
    public static double combatMusicVolume = 1.0D;
    /** 战斗期间是否一首接一首地循环 */
    public static boolean combatMusicLoop = true;

    static
    {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

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

        builder.comment("按生物群系播放指定音乐",
                        "需要先把音乐放进 config/musiclooper/music").push("biomeMusic");
        BIOME_MUSIC_ENABLED = builder
                .comment("是否启用本功能。",
                        "默认：false（不影响其它设置）")
                .define("enabled", false);
        BIOME_MUSIC_RULES = builder
                .comment("规则列表，每条格式为：群系ID=文件名1,文件名2",
                        "  具体群系：minecraft:plains（省略命名空间时默认 minecraft）",
                        "  群系标签：\"#minecraft:is_forest\"（一次覆盖所有森林类群系）",
                        "  兜底规则：*（前面都没命中时使用）",
                        "匹配顺序：具体群系 → 标签 → 兜底",
                        "文件不可用时该群系回退到全部曲目随机，不会彻底没声音")
                .defineListAllowEmpty("rules", List::<String>of, entry -> entry instanceof String);
        BIOME_MUSIC_MODE = builder
                .comment("命中的群系里，原版音乐怎么处理：",
                        "  ONLY_CUSTOM  只播配置的音乐（默认）",
                        "  MIX          配置的音乐与原版交替")
                .defineEnum("mode", BiomeMusicRules.Mode.ONLY_CUSTOM);
        builder.pop();

        builder.comment("战斗音乐",
                        "需要先把音乐放进 config/musiclooper/combat",
                        "这个文件夹里的曲子平时完全不播放，只在检测到战斗时响起",
                        "战斗结束后会自动停下并交还给常规音乐").push("combatMusic");
        COMBAT_MUSIC_ENABLED = builder
                .comment("是否启用战斗音乐。",
                        "文件夹为空时开启也不会有任何影响。",
                        "默认：true")
                .define("enabled", true);
        COMBAT_MUSIC_DETECT_RADIUS = builder
                .comment("检测敌对生物的半径（格）。",
                        "这个范围内的敌对生物如果把你设为目标，就算进入战斗。",
                        "默认：16")
                .defineInRange("detectRadius", 16, 1, 128);
        COMBAT_MUSIC_END_DELAY = builder
                .comment("战斗结束后再播放多少 tick 才停止（20 tick = 1 秒）。",
                        "避免打完最后一下音乐立刻断掉。",
                        "默认：100（5 秒）")
                .defineInRange("endDelay", 100, 0, 1200);
        COMBAT_MUSIC_TIMEOUT_TICKS = builder
                .comment("出手攻击之后，多少 tick 内仍然算「在战斗」。",
                        "默认：100（5 秒）")
                .defineInRange("combatTimeoutTicks", 100, 0, 1200);
        COMBAT_MUSIC_VOLUME = builder
                .comment("战斗音乐的音量倍率（0.0 ~ 1.0）。",
                        "实际音量 = 该倍率 × 游戏内“音乐”音量滑块。",
                        "默认：1.0")
                .defineInRange("volume", 1.0D, 0.0D, 1.0D);
        COMBAT_MUSIC_LOOP = builder
                .comment("战斗期间是否一首接一首地循环播放。",
                        "关闭后一场战斗只放一首。",
                        "默认：true")
                .define("loop", true);
        builder.pop();

        SPEC = builder.build();
    }

    /** 由 MusicLooper 注册到 mod 事件总线；ModConfigEvent 走的是 MOD 总线 */
    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent event)
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

        biomeMusicEnabled = BIOME_MUSIC_ENABLED.get();
        biomeMusicMode = BIOME_MUSIC_MODE.get();
        biomeMusicRules = BIOME_MUSIC_RULES.get().stream().map(String::valueOf).toList();
        BiomeMusicRules.apply(biomeMusicEnabled, biomeMusicRules, biomeMusicMode);

        combatMusicEnabled = COMBAT_MUSIC_ENABLED.get();
        combatMusicDetectRadius = COMBAT_MUSIC_DETECT_RADIUS.get();
        combatMusicEndDelay = COMBAT_MUSIC_END_DELAY.get();
        combatMusicTimeoutTicks = COMBAT_MUSIC_TIMEOUT_TICKS.get();
        combatMusicVolume = COMBAT_MUSIC_VOLUME.get();
        combatMusicLoop = COMBAT_MUSIC_LOOP.get();

        MusicLooper.LOGGER.debug("Music Looper 配置已加载：loopVanillaMusic={}, playCustomMusic={}, disableWhenEnough={} (>= {}), volume={}",
                loopVanillaMusic, playCustomMusic, disableVanillaMusicWhenEnoughCustom, customMusicThreshold, customMusicVolume);
    }
}
