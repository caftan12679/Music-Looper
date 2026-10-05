package com.arin.musiclooper;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Music Looper —— 让原版音乐连续循环播放，并支持把 config 文件夹里的第三方音乐混入播放列表。
 */
@Mod(MusicLooper.MODID)
public class MusicLooper
{
    public static final String MODID = "musiclooper";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MusicLooper(FMLJavaModLoadingContext context)
    {
        // 客户端配置：config/musiclooper-client.toml
        context.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        // 只在客户端加载客户端逻辑，服务端（或专用服务器）不会触碰下面的类
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            com.arin.musiclooper.client.ClientBootstrap.init(context.getModEventBus());
        }
    }
}
