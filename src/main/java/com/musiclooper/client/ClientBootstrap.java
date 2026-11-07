package com.musiclooper.client;

import com.musiclooper.MusicLooper;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 客户端入口：把音乐调度和指令接进 NeoForge 的事件总线。
 *
 * <p>这里注册的三个事件都挂在 <b>game 总线</b>（{@link NeoForge#EVENT_BUS}）上，
 * 不是模组生命周期总线。区别是：模组总线上跑的是「加载阶段」事件
 * （FMLClientSetupEvent、ModConfigEvent 之类），game 总线上跑的是
 * 「游戏运行中」事件（tick、指令注册、声音播放……）。</p>
 */
public final class ClientBootstrap
{
    private ClientBootstrap()
    {
    }

    public static void init()
    {
        // 每个客户端 tick 结束时驱动一次音乐调度（1.21 起 tick 事件拆成了 Pre / Post）
        NeoForge.EVENT_BUS.addListener(ClientBootstrap::onClientTickPost);

        // 拦下不该播的原版音乐。NeoForge 有 PlaySoundEvent，
        // 所以这里和 Forge 版写法一致，不需要 Mixin。
        NeoForge.EVENT_BUS.addListener(MusicController::onPlaySound);

        // /musiclooper reload | status | play | biome | combat
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
        ClientCommands.register(event);
    }
}
