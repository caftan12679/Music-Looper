package com.arin.musiclooper.client;

import com.arin.musiclooper.MusicLooper;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端入口：注册事件监听、准备音乐文件夹。
 */
public final class ClientBootstrap
{
    private ClientBootstrap()
    {
    }

    public static void init(IEventBus modEventBus)
    {
        modEventBus.addListener(ClientBootstrap::onClientSetup);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(MusicController::onPlaySound);
        MinecraftForge.EVENT_BUS.addListener(ClientCommands::register);
    }

    private static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() ->
        {
            CustomMusicLibrary.ensureFolder();
            CustomMusicLibrary.refresh();
            MusicLooper.LOGGER.info("Music Looper 已就绪。第三方音乐文件夹：{}", CustomMusicLibrary.MUSIC_DIR);
            MusicLooper.LOGGER.info("当前识别到的音频格式：{}", com.arin.musiclooper.client.audio.AudioDecoders.describeSupportedFormats());
        });
    }

    private static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        try
        {
            MusicController.tick(Minecraft.getInstance());
        }
        catch (Throwable throwable)
        {
            // 音乐出问题也不该让游戏崩掉
            MusicLooper.LOGGER.error("音乐调度出现异常", throwable);
        }
    }
}
