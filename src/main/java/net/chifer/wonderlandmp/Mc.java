package net.chifer.wonderlandmp;

import com.mojang.brigadier.CommandDispatcher;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class Mc {
    private static final int PORTAL_TRAVEL_EVENT = 1032;

    private Mc() {
    }

    static ResourceKey<Level> dim(Level level) {
        return level.m_46472_();
    }

    static ResourceKey<Level> dim(ServerPlayer player) {
        return player.m_9236_().m_46472_();
    }

    static String namespace(ResourceKey<Level> dim) {
        return dim.m_135782_().m_135827_();
    }

    static String id(ResourceKey<Level> dim) {
        return dim.m_135782_().toString();
    }

    static ServerLevel level(ServerPlayer player) {
        return player.m_284548_();
    }

    static MinecraftServer server(ServerPlayer player) {
        return player.f_8924_;
    }

    static MinecraftServer server(ServerLevel level) {
        return level.m_7654_();
    }

    static List<ServerPlayer> players(MinecraftServer server) {
        return server.m_6846_().m_11314_();
    }

    static ServerPlayer player(MinecraftServer server, UUID id) {
        return server.m_6846_().m_11259_(id);
    }

    static int tick(MinecraftServer server) {
        return server.m_129921_();
    }

    static UUID uuid(Entity entity) {
        return entity.m_20148_();
    }

    static String name(ServerPlayer player) {
        return player.m_36316_().getName();
    }

    static Vec3 pos(Entity entity) {
        return entity.m_20182_();
    }

    static Vec3 raised(Vec3 pos, double dy) {
        return new Vec3(pos.f_82479_, pos.f_82480_ + dy, pos.f_82481_);
    }

    static double distSqr(Entity a, Entity b) {
        return a.m_20280_(b);
    }

    static boolean active(ServerPlayer player) {
        return player.m_6084_() && !player.m_9232_();
    }

    static boolean spectator(ServerPlayer player) {
        return player.m_5833_();
    }

    static void changeDimension(ServerPlayer player, ServerLevel to, Vec3 at) {
        if (player.m_5803_()) {
            player.m_5796_();
        }
        if (player.f_36096_ != player.f_36095_) {
            player.m_6915_();
        }
        player.f_19789_ = 0.0F;
        player.f_8906_.m_9829_(new ClientboundGameEventPacket(ClientboundGameEventPacket.f_132157_, 0.0F));
        player.m_8999_(to, at.f_82479_, at.f_82480_, at.f_82481_, player.m_146908_(), player.m_146909_());
        player.f_8906_.m_9829_(new ClientboundPlayerAbilitiesPacket(player.m_150110_()));
        for (MobEffectInstance effect : player.m_21220_()) {
            player.f_8906_.m_9829_(new ClientboundUpdateMobEffectPacket(player.m_19879_(), effect));
        }
        player.f_8906_.m_9829_(new ClientboundLevelEventPacket(PORTAL_TRAVEL_EVENT, BlockPos.f_121853_, 0, false));
    }

    static void moveWithin(ServerPlayer player, Vec3 at) {
        player.f_19789_ = 0.0F;
        player.m_8999_(player.m_284548_(), at.f_82479_, at.f_82480_, at.f_82481_, player.m_146908_(), player.m_146909_());
    }

    static Entity sourceEntity(CommandSourceStack source) {
        return source.m_81373_();
    }

    static ServerLevel sourceLevel(CommandSourceStack source) {
        return source.m_81372_();
    }

    static Vec3 sourcePos(CommandSourceStack source) {
        return source.m_81371_();
    }

    static CommandSourceStack withEntity(CommandSourceStack source, Entity entity) {
        return source.m_81329_(entity);
    }

    static CommandDispatcher<CommandSourceStack> dispatcher(MinecraftServer server) {
        return server.m_129892_().m_82094_();
    }
}

/*
 * ═══════════════════════════════════════
 *  Mc — адаптер к Minecraft 1.20.1 (SRG-имена)
 * ═══════════════════════════════════════
 *  Мод компилируется прямо против production-jar Forge, где методы MC называются m_xxxx_/f_xxxx_.
 *  Все такие вызовы собраны здесь, остальной код работает через понятные имена.
 *  changeDimension повторяет телепорт MCreator из The Wonderland: экран загрузки (WIN_GAME 0),
 *  teleportTo, пересылка способностей/эффектов, звук портала (level event 1032).
 *  Перед телепортом: будит без PlayerWakeUpEvent (иначе мод бросит свой шанс похищения), закрывает контейнер,
 *  обнуляет fallDistance.
 * ═══════════════════════════════════════
*/
