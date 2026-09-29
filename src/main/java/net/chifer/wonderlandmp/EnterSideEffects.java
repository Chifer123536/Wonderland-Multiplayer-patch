package net.chifer.wonderlandmp;

import com.mojang.brigadier.ParseResults;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.mcreator.thewonderland.network.TheWonderlandModVariables;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class EnterSideEffects {
    private static final int WINDOW_TICKS = 10;

    private static final List<Arrival> arrivals = new ArrayList<>();
    private static ServerLevel phaseLevel;
    private static double phase;
    private static int phaseUntil;

    private record Arrival(UUID player, ResourceKey<Level> dim, Vec3 at, int until) {
    }

    private EnterSideEffects() {
    }

    static void guardPhase(ServerLevel level, int now) {
        phase = TheWonderlandModVariables.MapVariables.get(level).current_phase;
        phaseLevel = level;
        phaseUntil = now + WINDOW_TICKS;
    }

    static void enforcePhase() {
        if (phaseLevel == null) {
            return;
        }
        TheWonderlandModVariables.MapVariables vars = TheWonderlandModVariables.MapVariables.get(phaseLevel);
        if (vars.current_phase > phase) {
            vars.current_phase = phase;
            vars.syncData(phaseLevel);
        }
    }

    static void arrived(ServerPlayer player, ServerLevel level, Vec3 at, int now) {
        arrivals.add(new Arrival(Mc.uuid(player), Mc.dim(level), at, now + WINDOW_TICKS));
    }

    static void tick(int now) {
        if (phaseLevel != null) {
            enforcePhase();
            if (now >= phaseUntil) {
                phaseLevel = null;
            }
        }
        if (!arrivals.isEmpty()) {
            arrivals.removeIf(a -> now >= a.until());
        }
    }

    static void reset() {
        arrivals.clear();
        phaseLevel = null;
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (arrivals.isEmpty()) {
            return;
        }
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        CommandSourceStack source = parse.getContext().getSource();
        String command = parse.getReader().getString();
        if (Mc.sourceEntity(source) != null || !command.contains("@p")) {
            return;
        }
        ServerLevel level = Mc.sourceLevel(source);
        ResourceKey<Level> dim = Mc.dim(level);
        Vec3 pos = Mc.sourcePos(source);
        for (Iterator<Arrival> it = arrivals.iterator(); it.hasNext(); ) {
            Arrival arrival = it.next();
            if (!arrival.dim().equals(dim) || !arrival.at().equals(pos)) {
                continue;
            }
            it.remove();
            MinecraftServer server = Mc.server(level);
            ServerPlayer player = Mc.player(server, arrival.player());
            if (player != null) {
                String redirected = command.replace("@p", "@s");
                event.setParseResults(Mc.dispatcher(server).parse(redirected, Mc.withEntity(source, player)));
            }
            return;
        }
    }
}

/*
 * ═══════════════════════════════════════
 *  EnterSideEffects — побочки родного входа у затянутых игроков
 * ═══════════════════════════════════════
 *  Фаза: Enter-обработчики мода делают current_phase++ на каждый вход (часть — отложенно, до 3 тиков).
 *  Снимок фазы берётся перед рывком группы; 10 тиков любое превышение откатывается → +1 за группу, как у одного.
 *  @p: мод через 1–3 тика после входа шлёт "stopsound @p ..." из точки прибытия без сущности.
 *  Когда все стоят в одной точке, @p попадал бы в первого игрока. CommandEvent ловит команду из точной точки
 *  прибытия затянутого и переписывает @p → @s с этим игроком в качестве источника.
 * ═══════════════════════════════════════
*/
