package com.arin.musiclooper;

import com.arin.musiclooper.client.ClientBootstrap;
import com.arin.musiclooper.client.CustomMusicLibrary;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Music Looper —— 让原版音乐连续循环播放，并支持把 config 里的第三方音乐混入播放列表。
 *
 * <p>NeoForge 版入口。{@code dist = Dist.CLIENT} 表示这个模组只在客户端加载，
 * 专用服务器上不会被加载。</p>
 *
 * <p>NeoForge 1.21.1 的运行时命名空间就是 Mojang 官方名（mojmap），
 * 所以本模组编译出来即是成品，不需要任何重映射。</p>
 */
@Mod(value = MusicLooper.MODID, dist = Dist.CLIENT)
public class MusicLooper
{
    public static final String MODID = "musiclooper";
    public static final Logger LOGGER = LoggerFactory.getLogger("Music Looper");

    public MusicLooper(IEventBus modEventBus, ModContainer modContainer)
    {
        // 客户端配置：config/musiclooper-client.toml
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        // ModConfigEvent 在 MOD 总线上触发（它实现了 IModBusEvent），
        // 所以必须显式注册到 mod 事件总线，不能靠 @EventBusSubscriber 的默认值。
        modEventBus.addListener(Config::onConfigLoad);

        ClientBootstrap.init(modEventBus);

        CustomMusicLibrary.ensureFolder();
        CustomMusicLibrary.refresh();

        LOGGER.info("Music Looper 已就绪。第三方音乐文件夹：{}", CustomMusicLibrary.MUSIC_DIR);
        LOGGER.info("当前识别到的音频格式：{}",
                com.arin.musiclooper.client.audio.AudioDecoders.describeSupportedFormats());
    }
}
