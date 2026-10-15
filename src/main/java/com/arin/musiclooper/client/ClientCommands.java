package com.arin.musiclooper.client;

import com.arin.musiclooper.Config;
import com.arin.musiclooper.MusicLooper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

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
                        .executes(command -> status(command.getSource())))
                // /musiclooper play <文件名>  —— 立刻切到指定音乐
                .then(ClientCommandManager.literal("play")
                        .then(ClientCommandManager.argument("文件名", StringArgumentType.greedyString())
                                .suggests(ClientCommands::suggestTracks)
                                .executes(command -> play(command.getSource(),
                                        StringArgumentType.getString(command, "文件名")))))
                // /musiclooper biome —— 看看当前群系命中了什么
                .then(ClientCommandManager.literal("biome")
                        .executes(command -> biome(command.getSource())));

        dispatcher.register(root);
    }

    /** 给 play 指令做文件名联想 */
    private static CompletableFuture<Suggestions> suggestTracks(
            CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder)
    {
        String prefix = builder.getRemainingLowerCase();
        for (Path track : CustomMusicLibrary.playableTracks())
        {
            String name = track.getFileName().toString();
            if (prefix.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(prefix))
            {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    /** 立刻停掉当前音乐，改播指定文件 */
    private static int play(FabricClientCommandSource source, String fileName)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Path played = MusicController.playNow(minecraft, fileName);

        if (played == null)
        {
            source.sendError(Component.literal("找不到音乐：" + fileName
                    + "（可用 /musiclooper status 查看已有曲目）"));
            return 0;
        }

        source.sendFeedback(Component.literal("正在播放：" + played.getFileName()));
        return 1;
    }

    /** 当前生物群系命中了哪条规则 */
    private static int biome(FabricClientCommandSource source)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null)
        {
            source.sendError(Component.literal("要先进入世界才能查看群系信息"));
            return 0;
        }

        Holder<Biome> holder = minecraft.level.getBiome(minecraft.player.blockPosition());
        String id = holder.unwrapKey().map(key -> key.location().toString()).orElse("(未知)");

        source.sendFeedback(Component.literal("当前生物群系：" + id));
        source.sendFeedback(Component.literal("群系音乐功能："
                + (Config.biomeMusicEnabled ? "已启用（模式 " + Config.biomeMusicMode + "）" : "未启用")));

        List<Path> all = CustomMusicLibrary.playableTracks();
        List<Path> hit = BiomeMusicRules.pick(holder, all);
        if (hit == null)
        {
            source.sendFeedback(Component.literal("该群系没有命中规则 → 从全部 " + all.size() + " 首里随机"));
            return 0;
        }

        source.sendFeedback(Component.literal("命中 " + hit.size() + " 首："));
        for (Path track : hit)
        {
            source.sendFeedback(Component.literal("  · " + track.getFileName()));
        }
        return hit.size();
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
