package com.arin.musiclooper.client;

import com.arin.musiclooper.Config;
import com.arin.musiclooper.MusicLooper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * 客户端指令。
 *
 * <p>扫描第三方音乐文件夹是纯 I/O 操作，放在渲染线程上定时跑并不划算，
 * 所以改成<b>指令触发</b>：加完歌用 {@code /musiclooper reload} 重新扫一遍就行。</p>
 *
 * <ul>
 *     <li>{@code /musiclooper reload} —— 重新扫描音乐文件夹</li>
 *     <li>{@code /musiclooper status} —— 看看当前认出几首、配置是什么</li>
 * </ul>
 */
public final class ClientCommands
{
    private ClientCommands()
    {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context)
    {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal(MusicLooper.MODID)
                .then(ClientCommandManager.literal("reload")
                        .executes(command -> reload(command.getSource())))
                .then(ClientCommandManager.literal("status")
                        .executes(command -> status(command.getSource())));

        dispatcher.register(root);
    }

    private static int reload(FabricClientCommandSource source)
    {
        CustomMusicLibrary.refresh();

        int playable = CustomMusicLibrary.playableCount();
        int found = CustomMusicLibrary.knownCount();
        Path folder = CustomMusicLibrary.MUSIC_DIR;

        if (playable == 0 && found == 0)
        {
            source.sendFeedback(Component.literal("没有找到任何音乐文件。把音乐放进 " + folder));
        }
        else
        {
            source.sendFeedback(Component.literal(
                    "已重新扫描，识别到 " + playable + " 首可播放的第三方音乐（共发现 " + found + " 个文件）"));
        }

        if (Config.disableVanillaMusicWhenEnoughCustom)
        {
            boolean enough = playable >= Config.customMusicThreshold;
            source.sendFeedback(Component.literal(
                    "原版音乐禁用门槛：" + Config.customMusicThreshold
                            + " 首，当前" + (enough ? "已满足 → 只播第三方音乐" : "未满足 → 仍会播原版音乐")));
        }

        MusicLooper.LOGGER.info("手动重载第三方音乐：可播放 {} 首，共发现 {} 个文件", playable, found);
        return playable;
    }

    private static int status(FabricClientCommandSource source)
    {
        List<Path> tracks = CustomMusicLibrary.playableTracks();

        source.sendFeedback(Component.literal("音乐文件夹：" + CustomMusicLibrary.MUSIC_DIR));
        source.sendFeedback(Component.literal("可播放的第三方音乐：" + tracks.size() + " 首"));

        int shown = Math.min(tracks.size(), 10);
        for (int index = 0; index < shown; index++)
        {
            Path track = tracks.get(index);
            source.sendFeedback(Component.literal("  · " + track.getFileName()));
        }
        if (tracks.size() > shown)
        {
            source.sendFeedback(Component.literal("  …… 还有 " + (tracks.size() - shown) + " 首（详见日志）"));
        }

        source.sendFeedback(Component.literal(String.format(
                "配置：原版循环=%s，第三方音乐=%s，音量=%.2f，门槛禁用原版=%s(%d 首)",
                Config.loopVanillaMusic, Config.playCustomMusic, Config.customMusicVolume,
                Config.disableVanillaMusicWhenEnoughCustom, Config.customMusicThreshold)));
        return tracks.size();
    }
}
