package com.musiclooper;

import com.musiclooper.client.ClientBootstrap;
import com.musiclooper.client.CustomMusicLibrary;
import com.musiclooper.client.audio.AudioDecoders;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Music Looper —— 让原版音乐连续循环播放，并支持把 config 里的第三方音乐混入播放列表。
 *
 * <p>NeoForge 版入口。</p>
 *
 * <p>关于 {@code dist = Dist.CLIENT}：这句话告诉 NeoForge「这个类只在客户端构造」。
 * 专用服务器上 FML 根本不会 new 本类，所以不需要额外做侧别判断。</p>
 *
 * <p>关于命名空间：26.x 的 Minecraft 本身就没有混淆，NeoForge 也直接以官方名
 * （mojmap）发布，所以本模组编译出来就是成品，不需要 reobf / remap 那一步。</p>
 */
@Mod(value = MusicLooper.MODID, dist = Dist.CLIENT)
public class MusicLooper
{
    /** 模组 id，必须和 META-INF/neoforge.mods.toml 里的 modId 一致 */
    public static final String MODID = "musiclooper";

    /** 全模组共用的日志器；slf4j 由 NeoForge 运行时提供 */
    public static final Logger LOGGER = LoggerFactory.getLogger("Music Looper");

    /**
     * 构造器 = 模组加载时最先执行的代码。
     *
     * <p>参数由 FML 自动注入：这里只要 {@link IEventBus}（模组事件总线）。
     * 想要 ModContainer 的话再加一个参数即可，本模组用的是自己写的 TOML 配置，
     * 没走 NeoForge 的 ModConfigSpec，所以用不上。</p>
     */
    public MusicLooper(IEventBus modEventBus)
    {
        // 1. 读 config/musiclooper-client.toml（不存在就写一份默认值）
        Config.load();

        // 2. 确保音乐文件夹存在，并先扫一遍
        CustomMusicLibrary.ensureFolder();
        CustomMusicLibrary.refresh();

        // 3. 把 tick / 指令 / 声音拦截接到 NeoForge 的事件总线
        ClientBootstrap.init();

        LOGGER.info("Music Looper 已就绪。第三方音乐文件夹：{}", CustomMusicLibrary.MUSIC_DIR);
        LOGGER.info("当前识别到的音频格式：{}", AudioDecoders.describeSupportedFormats());
    }
}
