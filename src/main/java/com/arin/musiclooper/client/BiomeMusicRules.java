package com.arin.musiclooper.client;

import com.arin.musiclooper.MusicLooper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按生物群系挑选第三方音乐。
 *
 * <p>配置里每条规则的格式是 {@code 群系ID=文件名1,文件名2}：</p>
 * <ul>
 *     <li>具体群系：{@code minecraft:plains}（省略命名空间时默认 {@code minecraft}）</li>
 *     <li>群系标签：{@code #minecraft:is_forest}（一次覆盖所有森林类群系）</li>
 *     <li>兜底规则：{@code *}（前面都没命中时使用）</li>
 * </ul>
 *
 * <p>匹配顺序：具体群系 → 标签 → 兜底。命中后会把文件名解析成实际可播放的曲目；
 * 如果一个都没解析出来（文件被删了或解码失败），返回 {@code null} 表示回退到全部曲目，
 * 避免玩家进到这个群系反而彻底没声音。</p>
 */
public final class BiomeMusicRules
{
    /** 命中群系后，原版音乐怎么处理 */
    public enum Mode
    {
        /** 该群系只播放配置里指定的音乐 */
        ONLY_CUSTOM,
        /** 配置的音乐与原版音乐交替播放 */
        MIX
    }

    /** 配置里的规则原文；与上次相同就不重复解析 */
    private static volatile List<String> appliedRules = List.of();
    private static volatile boolean enabled;
    private static volatile Mode mode = Mode.ONLY_CUSTOM;

    private static Map<String, List<String>> exactRules = Map.of();
    private static Map<String, List<String>> tagRules = Map.of();
    private static List<String> wildcardFiles = List.of();

    /** TagKey 构造有开销，按名字缓存 */
    private static final Map<String, TagKey<Biome>> TAG_CACHE = new ConcurrentHashMap<>();

    private BiomeMusicRules()
    {
    }

    public static boolean isEnabled()
    {
        return enabled;
    }

    public static Mode mode()
    {
        return mode;
    }

    /** 由各版本的 Config 在加载 / 重载时调用 */
    public static void apply(boolean enabledIn, List<String> rules, Mode modeIn)
    {
        enabled = enabledIn;
        mode = modeIn != null ? modeIn : Mode.ONLY_CUSTOM;

        List<String> copy = rules == null ? List.of() : List.copyOf(rules);
        if (copy.equals(appliedRules))
        {
            return;
        }
        appliedRules = copy;

        Map<String, List<String>> exact = new LinkedHashMap<>();
        Map<String, List<String>> tags = new LinkedHashMap<>();
        List<String> wildcard = List.of();

        for (String rule : copy)
        {
            if (rule == null || rule.isBlank())
            {
                continue;
            }

            int equals = rule.indexOf('=');
            if (equals <= 0)
            {
                MusicLooper.LOGGER.warn("忽略格式错误的群系音乐规则（应为 群系ID=文件名1,文件名2）：{}", rule);
                continue;
            }

            String key = rule.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            List<String> files = splitFiles(rule.substring(equals + 1));
            if (key.isEmpty())
            {
                continue;
            }

            if ("*".equals(key))
            {
                wildcard = files;
            }
            else if (key.startsWith("#"))
            {
                tags.put(normalizeId(key.substring(1)), files);
            }
            else
            {
                exact.put(normalizeId(key), files);
            }
        }

        exactRules = Map.copyOf(exact);
        tagRules = Map.copyOf(tags);
        wildcardFiles = List.copyOf(wildcard);

        MusicLooper.LOGGER.info("群系音乐规则已加载：具体群系 {} 条，标签 {} 条，兜底 {} 条（模式 {}）",
                exactRules.size(), tagRules.size(), wildcardFiles.size(), mode);
    }

    /**
     * 取当前群系命中的曲目。
     *
     * @return 命中的曲目；未启用、未命中、或解析不出可用文件时返回 {@code null}（表示按原逻辑处理）
     */
    public static List<Path> pick(Holder<Biome> biome, List<Path> available)
    {
        if (!enabled || biome == null || available == null || available.isEmpty())
        {
            return null;
        }
        if (exactRules.isEmpty() && tagRules.isEmpty() && wildcardFiles.isEmpty())
        {
            return null;
        }

        Optional<ResourceKey<Biome>> key = biome.unwrapKey();
        String id = key.map(entry -> entry.location().toString()).orElse(null);

        List<String> files = null;
        if (id != null)
        {
            files = exactRules.get(id);
            if (files == null)
            {
                for (Map.Entry<String, List<String>> entry : tagRules.entrySet())
                {
                    if (biome.is(tagOf(entry.getKey())))
                    {
                        files = entry.getValue();
                        break;
                    }
                }
            }
        }

        if (files == null)
        {
            files = wildcardFiles;
        }
        if (files == null || files.isEmpty())
        {
            return null;
        }

        List<Path> resolved = resolve(files, available);
        if (resolved.isEmpty())
        {
            MusicLooper.LOGGER.debug("群系 {} 的规则没有匹配到任何可用文件，回退到全部曲目", id);
            return null;
        }
        return resolved;
    }

    /**
     * 这个群系是否应该拦下原版音乐。
     *
     * <p>只有 {@link Mode#ONLY_CUSTOM} 且规则真的命中可用文件时才拦；
     * {@link Mode#MIX} 模式下原版音乐要照常参与交替。</p>
     */
    public static boolean blocksVanilla(Holder<Biome> biome, List<Path> available)
    {
        return mode == Mode.ONLY_CUSTOM && pick(biome, available) != null;
    }

    /** {@code plains} → {@code minecraft:plains} */
    private static String normalizeId(String value)
    {
        String id = value.trim().toLowerCase(Locale.ROOT);
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    private static TagKey<Biome> tagOf(String id)
    {
        return TAG_CACHE.computeIfAbsent(id, name -> TagKey.create(Registries.BIOME, rl(name)));
    }

    /** 版本差异：1.20.1 用 new ResourceLocation(s)，1.21.1 用 ResourceLocation.parse(s) */
    private static ResourceLocation rl(String value)
    {
        return new ResourceLocation(value);
    }

    /** 逗号分隔的文件名列表，去掉空白与空项 */
    private static List<String> splitFiles(String value)
    {
        List<String> result = new ArrayList<>();
        for (String part : value.split(","))
        {
            String name = part.trim();
            if (!name.isEmpty())
            {
                result.add(name);
            }
        }
        return result;
    }

    /** 把配置里的文件名匹配到实际扫描到的曲目（按文件名比较，忽略大小写与子目录） */
    private static List<Path> resolve(List<String> names, List<Path> available)
    {
        List<Path> result = new ArrayList<>();
        for (String name : names)
        {
            String wanted = name.toLowerCase(Locale.ROOT);
            for (Path path : available)
            {
                if (path.getFileName().toString().toLowerCase(Locale.ROOT).equals(wanted))
                {
                    result.add(path);
                    break;
                }
            }
        }
        return result;
    }
}
