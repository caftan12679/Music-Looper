package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;

/**
 * Fabric API 的客户端 tick 事件 —— <b>仅用于编译</b>，不会打进 jar。
 * 真实签名：{@code END_CLIENT_TICK = Event<EndTick>}，
 * {@code EndTick.onEndTick(net.minecraft.class_310)}。
 */
public final class ClientTickEvents
{
    /** 每一个客户端 tick 结束时触发 */
    public static final Event<EndTick> END_CLIENT_TICK = null;

    private ClientTickEvents()
    {
    }

    public interface EndTick
    {
        void onEndTick(Minecraft client);
    }
}
