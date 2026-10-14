package com.arin.musiclooper;

import com.arin.musiclooper.client.ClientBootstrap;
import com.arin.musiclooper.client.CustomMusicLibrary;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Music Looper —— 让原版音乐连续循环播放，并支持把 config 里的第三方音乐混入播放列表。
 *
 * <p>Fabric 版入口。与 Forge 版的差别只在“怎么接进游戏”，音乐逻辑完全一致。</p>
 */
public class MusicLooper implements ClientModInitializer
{
    public static final String MODID = "musiclooper";
    public static final Logger LOGGER = LoggerFactory.getLogger("Music Looper");

    @Override
    public void onInitializeClient()
    {
        Config.load();

        CustomMusicLibrary.ensureFolder();
        CustomMusicLibrary.refresh();

        ClientBootstrap.init();

        LOGGER.info("Music Looper 已就绪。第三方音乐文件夹：{}", CustomMusicLibrary.MUSIC_DIR);
        LOGGER.info("当前识别到的音频格式：{}", com.arin.musiclooper.client.audio.AudioDecoders.describeSupportedFormats());
    }
}
