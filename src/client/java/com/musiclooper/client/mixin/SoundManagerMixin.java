package com.musiclooper.client.mixin;

import com.musiclooper.client.MusicController;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦下“不该出现”的原版音乐。
 *
 * <p>Forge/NeoForge 那边有 {@code PlaySoundEvent} 可以直接取消，Fabric 没有等价事件，
 * 所以这里挂在 {@code SoundManager.play} 的入口上：判定为不该播就直接拦掉。</p>
 *
 * <h3>26.1.2 的变化</h3>
 *
 * <p>1.21.1 里 {@code SoundManager.play(SoundInstance)} 返回 {@code void}，注入时用
 * {@code CallbackInfo} + {@code info.cancel()} 即可。</p>
 *
 * <p>26.1.2 把它改成了返回 {@link SoundEngine.PlayResult}，于是注入方式也要跟着变：</p>
 * <ul>
 *     <li>回调类型从 {@code CallbackInfo} 换成 {@code CallbackInfoReturnable<PlayResult>}</li>
 *     <li>拦截时调 {@code cir.setReturnValue(...)} —— <b>注意这里返回的是
 *         {@link SoundEngine.PlayResult#NOT_STARTED}，而不是 null</b>。
 *         枚举里本来就有「没开始播放」这个值，调用方拿到的语义是明确的，
 *         不会因为 null 而 NPE。</li>
 * </ul>
 *
 * <p>另外：26.1.2 的客户端 jar 是未混淆的，所以这里直接用真实类名，
 * 不需要像 1.21.1 那样写 intermediary 的 {@code class_1144} / {@code method_4873}。</p>
 */
@Mixin(SoundManager.class)
public class SoundManagerMixin
{
    /**
     * 在 {@code play} 入口处询问调度器要不要拦。
     *
     * @param sound 即将播放的声音实例（只关心音乐通道的）
     * @param cir   返回值回调，拦下时写入 {@code NOT_STARTED}
     */
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void musiclooper$blockVanillaMusic(SoundInstance sound,
                                               CallbackInfoReturnable<SoundEngine.PlayResult> cir)
    {
        if (MusicController.shouldBlock(sound))
        {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}
