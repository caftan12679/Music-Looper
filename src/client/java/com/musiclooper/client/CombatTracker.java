package com.musiclooper.client;

import com.musiclooper.Config;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 判断玩家当前是否处于战斗中。
 *
 * <p>每个客户端 tick 调一次 {@link #update(Player)}。满足下面任意一条就算在战斗：</p>
 * <ol>
 *     <li><b>挨打</b>：{@code hurtTime > 0}；</li>
 *     <li><b>在攻击</b>：最近打过的目标还活着，而且距离上次出手没超过 {@code combatTimeoutTicks}；</li>
 *     <li><b>被敌对生物盯上</b>：{@code detectRadius} 内有存活的敌对生物，且它把玩家设为目标。</li>
 * </ol>
 *
 * <p>为了让“打完最后一下”不会立刻把音乐掐掉，这里用 {@code heldTicks} 做一段缓冲：
 * 检测到战斗就把它重置成 {@code endDelay}，没检测到就递减。只有减到 0 才认为战斗结束。</p>
 *
 * <p>纯客户端逻辑，只看客户端可见的实体信息，不需要服务端配合。</p>
 */
public final class CombatTracker
{
    /** 战斗结束后的剩余缓冲 tick 数；&gt; 0 表示仍在战斗状态 */
    private static int heldTicks;
    /** 本 tick 是否检测到了战斗动作 */
    private static boolean detectedThisTick;

    private CombatTracker()
    {
    }

    /**
     * 更新战斗状态。
     *
     * @param player 客户端玩家；为 null 时清空状态
     * @return 当前是否处于战斗中
     */
    public static boolean update(Player player)
    {
        if (player == null)
        {
            reset();
            return false;
        }

        if (!Config.combatMusicEnabled)
        {
            // 功能关掉时不维持任何状态，避免重新打开后还残留“战斗中”
            detectedThisTick = false;
            heldTicks = 0;
            return false;
        }

        detectedThisTick = detect(player);

        if (detectedThisTick)
        {
            // 只要还在打，倒计时一直满格
            heldTicks = Math.max(1, Config.combatMusicEndDelay);
        }
        else if (heldTicks > 0)
        {
            heldTicks--;
        }

        return heldTicks > 0;
    }

    /** 本 tick 是否真的检测到了战斗动作（不含缓冲） */
    public static boolean detectedThisTick()
    {
        return detectedThisTick;
    }

    /** 剩余缓冲 tick 数；0 表示战斗已结束 */
    public static int heldTicks()
    {
        return heldTicks;
    }

    public static void reset()
    {
        heldTicks = 0;
        detectedThisTick = false;
    }

    // ------------------------------------------------------------------
    // 判定
    // ------------------------------------------------------------------

    private static boolean detect(Player player)
    {
        // ① 自己正在受伤
        if (player.hurtTime > 0)
        {
            return true;
        }

        // ② 刚打过谁，且对方还活着
        if (recentlyHurtTargetAlive(player))
        {
            return true;
        }

        // ③ 附近有敌对生物锁定了自己
        return hostileTargetingPlayer(player);
    }

    private static boolean recentlyHurtTargetAlive(Player player)
    {
        LivingEntity target;
        try
        {
            target = player.getLastHurtMob();
        }
        catch (Throwable ignored)
        {
            return false;
        }

        if (target == null || !target.isAlive() || target.isDeadOrDying())
        {
            return false;
        }

        int since = player.tickCount - player.getLastHurtMobTimestamp();
        return since >= 0 && since <= Math.max(1, Config.combatMusicTimeoutTicks);
    }

    private static boolean hostileTargetingPlayer(Player player)
    {
        Level level = player.level();
        if (level == null)
        {
            return false;
        }

        double radius = Math.max(1.0D, Config.combatMusicDetectRadius);
        AABB box = player.getBoundingBox().inflate(radius);

        List<Monster> monsters;
        try
        {
            monsters = level.getEntitiesOfClass(Monster.class, box);
        }
        catch (Throwable ignored)
        {
            return false;
        }

        for (Monster monster : monsters)
        {
            if (monster == null || !monster.isAlive() || monster.isDeadOrDying())
            {
                continue;
            }
            // Monster 继承自 Mob，getTarget() 返回它当前的攻击目标
            if (monster.getTarget() == player)
            {
                return true;
            }
        }
        return false;
    }
}
