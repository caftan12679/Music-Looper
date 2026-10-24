package net.fabricmc.fabric.api.client.command.v2;

import net.minecraft.network.chat.Component;

/**
 * Fabric API 的客户端指令源 —— <b>仅用于编译</b>，不会打进 jar。
 *
 * <p>真实接口还继承了 {@code net.minecraft.class_2172}（= SharedSuggestionProvider）
 * 并提供 {@code sendError} / {@code getClient} / {@code getPlayer} / {@code getWorld} 等。
 * 这里只声明本模组实际用到的方法；反正桩类不会进 jar，
 * 调用点重映射后的描述符与真实接口一致即可。</p>
 */
public interface FabricClientCommandSource
{
    void sendFeedback(Component component);

    void sendError(Component component);
}
