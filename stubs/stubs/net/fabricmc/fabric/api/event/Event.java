package net.fabricmc.fabric.api.event;

/**
 * Fabric API 的 {@code Event<T>} —— <b>仅用于编译</b>，不会打进 jar。
 *
 * <p>为什么不直接拿真实的 fabric-api jar 来编译：Fabric API 是编译在
 * <b>intermediary</b> 命名空间上的（它引用 {@code net.minecraft.class_310} 这种名字），
 * 而我们的源码是 mojmap（{@code net.minecraft.client.Minecraft}），混着用会类型不匹配。</p>
 *
 * <p>这里的桩用 mojmap 签名声明；产物重映射成 intermediary 之后，
 * 调用点的描述符正好与真实 Fabric API 逐位对齐，所以运行时能正常链接。</p>
 */
public abstract class Event<T>
{
    public abstract void register(T listener);
}
