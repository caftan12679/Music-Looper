package net.fabricmc.fabric.api.client.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.commands.CommandBuildContext;

/**
 * Fabric API 的客户端指令注册回调 —— <b>仅用于编译</b>，不会打进 jar。
 *
 * <p>真实签名：{@code register(CommandDispatcher<FabricClientCommandSource>, net.minecraft.class_7157)}。</p>
 */
public interface ClientCommandRegistrationCallback
{
    Event<ClientCommandRegistrationCallback> EVENT = null;

    void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context);
}
