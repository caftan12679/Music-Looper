package com.arin.musiclooper.client;

import com.arin.musiclooper.MusicLooper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * 客户端入口：把音乐调度和指令接进 Fabric 的回调。
 */
public final class ClientBootstrap
{
    private ClientBootstrap()
    {
    }

    public static void init()
    {
        // 每个客户端 tick 结束时驱动一次音乐调度
        ClientTickEvents.END_CLIENT_TICK.register(ClientBootstrap::onClientTick);

        // /musiclooper reload | status
        ClientCommandRegistrationCallback.EVENT.register(ClientCommands::register);
    }

    private static void onClientTick(Minecraft client)
    {
        try
        {
            MusicController.tick(client);
        }
        catch (Throwable throwable)
        {
            // 音乐出问题也不该让游戏崩掉
            MusicLooper.LOGGER.error("音乐调度出现异常", throwable);
        }
    }
}
