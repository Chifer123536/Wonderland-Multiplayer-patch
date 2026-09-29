package net.chifer.wonderlandmp;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

final class WmpConfig {
    static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.BooleanValue ENABLED;
    static final ForgeConfigSpec.IntValue PULL_DELAY_TICKS;
    static final ForgeConfigSpec.BooleanValue PULL_FROM_ANY_DIMENSION;
    static final ForgeConfigSpec.BooleanValue PULL_SPECTATORS;
    static final ForgeConfigSpec.BooleanValue REGROUP;
    static final ForgeConfigSpec.IntValue REGROUP_DISTANCE;
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_DIMENSIONS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("group_entry");
        ENABLED = b
                .comment("Когда одного игрока затягивает в Вондерленд снаружи, остальные попадают в то же измерение.")
                .define("enabled", true);
        PULL_DELAY_TICKS = b
                .comment("Через сколько тиков после входа первого игрока затягивать остальных (20 тиков = 1 сек).",
                        "Нужна пауза, чтобы мод успел поставить первого игрока на место.")
                .defineInRange("pullDelayTicks", 10, 5, 200);
        PULL_FROM_ANY_DIMENSION = b
                .comment("true — затягивать игроков из любого измерения (Незер, Энд и т.д.).",
                        "false — только тех, кто был в том же измерении, откуда затянуло первого.")
                .define("pullFromAnyDimension", true);
        PULL_SPECTATORS = b
                .comment("Затягивать игроков в режиме наблюдателя.")
                .define("pullSpectators", false);
        REGROUP = b
                .comment("Если мод раскидал затянутых по случайным точкам, собрать их к первому игроку.")
                .define("regroup", true);
        REGROUP_DISTANCE = b
                .comment("Дальше скольки блоков от первого игрока считать, что игрока раскидало.")
                .defineInRange("regroupDistance", 8, 1, 256);
        EXCLUDED_DIMENSIONS = b
                .comment("Измерения, вход в которые НЕ затягивает остальных. Пример: [\"the_wonderland:dreamworld\"]")
                .defineListAllowEmpty(List.of("excludedDimensions"), List::of, o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    private WmpConfig() {
    }
}

/*
 * ═══════════════════════════════════════
 *  WmpConfig — config/wonderland_mp-common.toml
 * ═══════════════════════════════════════
 *  Настройки группового входа: вкл/выкл, задержка, откуда тянуть, наблюдатели, сбор в одну точку, исключения.
 * ═══════════════════════════════════════
*/
