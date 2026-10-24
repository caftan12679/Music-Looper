package com.arin.musiclooper.client.mixin;

import com.arin.musiclooper.client.MusicController;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦下“不该出现”的原版音乐。
 *
 * <p>Forge 那边有 {@code PlaySoundEvent} 可以直接取消，Fabric 没有等价事件，
 * 所以这里挂在 {@code SoundManager.play} 的入口上：判定为不该播就直接 cancel。</p>
 *
 * <p><b>注意命名空间：</b>这个文件先用 mojmap 编译，之后整个产物会被重映射成
 * intermediary。而 Mixin 注解里的字符串<b>不会</b>被重映射，所以下面这两个字符串
 * 直接写成 intermediary 名，与编译后重映射的实际类/方法完全一致：</p>
 *
 * <pre>
 *   net.minecraft.class_1144  = SoundManager
 *   method_4873(Lnet/minecraft/class_1113;)V = play(SoundInstance)V
 *   net.minecraft.class_1113  = SoundInstance
 * </pre>
 */
@Mixin(targets = "net.minecraft.class_1144")
public class SoundManagerMixin
{
    @Inject(method = "method_4873(Lnet/minecraft/class_1113;)V", at = @At("HEAD"), cancellable = true)
    private void musiclooper$blockVanillaMusic(SoundInstance sound, CallbackInfo info)
    {
        if (MusicController.shouldBlock(sound))
        {
            info.cancel();
        }
    }
}
