package com.arin.musiclooper.client;

import com.arin.musiclooper.MusicLooper;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * 客户端入口：把音乐调度和指令接进 NeoForge 的事件总线。
 */
public final class ClientBootstrap
{
    private ClientBootstrap()
    {
    }

    public static void init(IEventBus modEventBus)
    {
        // 每个客户端 tick 结束时驱动一次音乐调度（1.21 起拆成了 Pre / Post）
        NeoForge.EVENT_BUS.addListener(ClientBootstrap::onClientTickPost);

        // 拦下不该播的原版音乐。NeoForge 有 PlaySoundEvent，
        // 所以这里和 Forge 版写法一致，不需要 Mixin。
        NeoForge.EVENT_BUS.addListener(MusicController::onPlaySound);

        // /musiclooper reload | status
        NeoForge.EVENT_BUS.addListener(ClientBootstrap::onRegisterClientCommands);
    }

    private static void onClientTickPost(ClientTickEvent.Post event)
    {
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

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event)
    {
        ClientCommands.register(event.getDispatcher());
    }
}
