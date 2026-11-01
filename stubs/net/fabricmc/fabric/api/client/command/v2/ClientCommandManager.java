package net.fabricmc.fabric.api.client.command.v2;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

/**
 * Fabric API 的客户端指令工具 —— <b>仅用于编译</b>，不会打进 jar。
 * 与 Brigadier 的 {@code Commands.literal} 等价，只是把指令源换成客户端的。
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
}
