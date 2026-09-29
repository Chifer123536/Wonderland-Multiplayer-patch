package net.chifer.wonderlandmp;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

public final class GroupEntry {
    static final String WONDERLAND = "the_wonderland";
    private static final int REGROUP_DELAY_TICKS = 5;
    private static final double ARRIVAL_STEP = 1.0E-4;
    private static final Logger LOG = LogUtils.getLogger();

    private static final Set<UUID> moving = new HashSet<>();
    private static Pull pending;
    private static Regroup regroup;

    private record Pull(UUID leader, ResourceKey<Level> from, int dueTick, Set<UUID> coEntrants) {
    }

    private record Regroup(UUID leader, List<UUID> members, int dueTick) {
    }

    private GroupEntry() {
    }

    static boolean isWonderland(ResourceKey<Level> dim) {
        return WONDERLAND.equals(Mc.namespace(dim));
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !WmpConfig.ENABLED.get()) {
            return;
        }
        UUID id = Mc.uuid(player);
        if (moving.contains(id) || isWonderland(event.getFrom()) || !isWonderland(event.getTo()) || isExcluded(event.getTo())) {
            return;
        }
        if (pending == null) {
            int due = Mc.tick(Mc.server(player)) + WmpConfig.PULL_DELAY_TICKS.get();
            pending = new Pull(id, event.getFrom(), due, new LinkedHashSet<>());
        } else if (!pending.leader().equals(id)) {
            pending.coEntrants().add(id);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int now = Mc.tick(server);
        if (pending != null && now >= pending.dueTick()) {
            Pull job = pending;
            pending = null;
            try {
                pull(server, job, now);
            } catch (RuntimeException e) {
                LOG.error("[wonderland_mp] group pull failed", e);
            }
        }
        if (regroup != null && now >= regroup.dueTick()) {
            Regroup job = regroup;
            regroup = null;
            try {
                regroup(server, job);
            } catch (RuntimeException e) {
                LOG.error("[wonderland_mp] regroup failed", e);
            }
        }
        EnterSideEffects.tick(now);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        reset();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        reset();
    }

    private static void pull(MinecraftServer server, Pull job, int now) {
        ServerPlayer leader = pickLeader(server, job);
        if (leader == null) {
            return;
        }
        ServerLevel target = Mc.level(leader);
        Vec3 at = Mc.pos(leader);
        UUID leaderId = Mc.uuid(leader);

        List<ServerPlayer> followers = new ArrayList<>();
        for (ServerPlayer p : Mc.players(server)) {
            if (p != leader && canFollow(p, job.from())) {
                followers.add(p);
            }
        }

        List<UUID> members = new ArrayList<>(job.coEntrants());
        members.remove(leaderId);

        if (!followers.isEmpty()) {
            EnterSideEffects.guardPhase(target, now);
            int moved = 0;
            for (ServerPlayer follower : followers) {
                Vec3 arrival = Mc.raised(at, ARRIVAL_STEP * (moved + 1));
                UUID id = Mc.uuid(follower);
                moving.add(id);
                try {
                    Mc.changeDimension(follower, target, arrival);
                } catch (RuntimeException e) {
                    LOG.error("[wonderland_mp] failed to move {}", id, e);
                } finally {
                    moving.remove(id);
                }
                EnterSideEffects.enforcePhase();
                if (Mc.level(follower) == target) {
                    EnterSideEffects.arrived(follower, target, arrival, now);
                    members.add(id);
                    moved++;
                }
            }
            LOG.info("[wonderland_mp] {} entered {}, pulled {} player(s)", Mc.name(leader), Mc.id(Mc.dim(target)), moved);
        }

        if (WmpConfig.REGROUP.get() && !members.isEmpty()) {
            regroup = new Regroup(leaderId, members, now + REGROUP_DELAY_TICKS);
        }
    }

    private static void regroup(MinecraftServer server, Regroup job) {
        ServerPlayer leader = Mc.player(server, job.leader());
        if (leader == null || !Mc.active(leader)) {
            return;
        }
        ServerLevel level = Mc.level(leader);
        double max = WmpConfig.REGROUP_DISTANCE.get();
        Vec3 at = Mc.pos(leader);
        int moved = 0;
        for (UUID id : job.members()) {
            ServerPlayer p = Mc.player(server, id);
            if (p != null && Mc.active(p) && Mc.level(p) == level && Mc.distSqr(p, leader) > max * max) {
                Mc.moveWithin(p, at);
                moved++;
            }
        }
        if (moved > 0) {
            LOG.info("[wonderland_mp] regrouped {} player(s) to {}", moved, Mc.name(leader));
        }
    }

    private static ServerPlayer pickLeader(MinecraftServer server, Pull job) {
        ServerPlayer leader = insideWonderland(server, job.leader());
        if (leader != null) {
            return leader;
        }
        for (UUID id : job.coEntrants()) {
            leader = insideWonderland(server, id);
            if (leader != null) {
                return leader;
            }
        }
        return null;
    }

    private static ServerPlayer insideWonderland(MinecraftServer server, UUID id) {
        ServerPlayer p = Mc.player(server, id);
        if (p == null || !Mc.active(p)) {
            return null;
        }
        ResourceKey<Level> dim = Mc.dim(p);
        return isWonderland(dim) && !isExcluded(dim) ? p : null;
    }

    private static boolean canFollow(ServerPlayer p, ResourceKey<Level> from) {
        if (!Mc.active(p) || isWonderland(Mc.dim(p))) {
            return false;
        }
        if (Mc.spectator(p) && !WmpConfig.PULL_SPECTATORS.get()) {
            return false;
        }
        return WmpConfig.PULL_FROM_ANY_DIMENSION.get() || Mc.dim(p).equals(from);
    }

    private static boolean isExcluded(ResourceKey<Level> dim) {
        return WmpConfig.EXCLUDED_DIMENSIONS.get().contains(Mc.id(dim));
    }

    private static void reset() {
        moving.clear();
        pending = null;
        regroup = null;
        EnterSideEffects.reset();
    }
}

/*
 * ═══════════════════════════════════════
 *  GroupEntry — групповой вход в Вондерленд
 * ═══════════════════════════════════════
 *  Триггер: PlayerChangedDimensionEvent снаружи → в измерение the_wonderland:* (не из excludedDimensions).
 *  Переходы внутри Вондерленда и выходы не трогаются: дальше каждый сам по себе.
 *  Через pullDelayTicks (мод успевает поставить первого) все игроки снаружи телепортируются к нему
 *  тем же способом, что и в моде → у каждого срабатывает родной Enter-обработчик измерения.
 *  Точка прибытия у каждого своя (+1e-4 по Y), чтобы EnterSideEffects мог опознать его команды.
 *  Одновременно зашедшие сами (coEntrants) не дублируют рывок, а только собираются к первому.
 *  Через 5 тиков regroup стягивает к первому тех, кого мод раскидал дальше regroupDistance.
 *  Свои телепорты помечены в moving — по ним событие игнорируется. Ошибки ловятся, сервер не падает.
 * ═══════════════════════════════════════
*/
