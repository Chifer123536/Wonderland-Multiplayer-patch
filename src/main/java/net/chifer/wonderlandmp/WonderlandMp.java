package net.chifer.wonderlandmp;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(WonderlandMp.MOD_ID)
public final class WonderlandMp {
    public static final String MOD_ID = "wonderland_mp";

    @SuppressWarnings("removal")
    public WonderlandMp() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WmpConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(GroupEntry.class);
        MinecraftForge.EVENT_BUS.register(EnterSideEffects.class);
    }
}

/*
 * ═══════════════════════════════════════
 *  WonderlandMp — точка входа Forge
 * ═══════════════════════════════════════
 *  Регистрирует конфиг (COMMON) и статические обработчики событий в EVENT_BUS.
 *  Вся логика серверная: работает и на выделенном сервере, и у хоста локального мира (LAN / e4steam).
 * ═══════════════════════════════════════
*/
