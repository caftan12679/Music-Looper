package net.fabricmc.fabric.api.client.command.v2;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

/**
 * Fabric API 的客户端指令工具 —— <b>仅用于编译</b>，不会打进 jar。
 * 与 Brigadier 的 {@code Commands.literal} / {@code Commands.argument} 等价，
 * 只是把指令源换成客户端的。
 */
public final class ClientCommandManager
{
    private ClientCommandManager()
    {
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name)
    {
        throw new UnsupportedOperationException("compile-time stub");
    }

    public static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type)
    {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
