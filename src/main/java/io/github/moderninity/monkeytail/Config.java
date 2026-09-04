package io.github.moderninity.monkeytail;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's three settings, written to {@code config/monkeytail-client.toml} the first time
 * the game runs.
 *
 * <p>Each setting is copied into a plain static field whenever the file is loaded or edited.
 * That is not premature tidying: {@link #emptyOffhand} is read from inside
 * {@code Player.getItemBySlot}, which the game calls thousands of times a second, and a
 * {@code ModConfigSpec} lookup walks a map every time.
 *
 * <p>This is a CLIENT config, so on a dedicated server the file does not exist and these fields
 * keep their defaults. Nothing on the server reads them — see {@link Prefs} for how the two
 * settings the server does need get there.
 */
public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue EMPTY_OFFHAND = BUILDER
            .comment("Empty your offhand too while the extra slot is selected.",
                     "Off means your shield or torch stays visible when you put your main hand down.")
            .define("empty_offhand", true);

    private static final ModConfigSpec.BooleanValue PRESS_AND_HOLD = BUILDER
            .comment("How the key behaves.",
                     "false: tap to put your hands down, tap again to take the item back.",
                     "true:  hands are empty only for as long as you hold the key.")
            .define("press_and_hold", false);

    private static final ModConfigSpec.BooleanValue ROUTE_TO_BACKPACKS = BUILDER
            .comment("If your inventory is completely full when something is put in your hand,",
                     "try to push it into a bag or backpack you are carrying or wearing before",
                     "giving up and dropping it on the ground.")
            .define("route_to_backpacks", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** Live values. Defaults here match the {@code define} calls above, for the brief moment
     *  before the file is read and forever on a dedicated server. */
    public static boolean emptyOffhand = true;
    public static boolean pressAndHold = false;
    public static boolean routeToBackpacks = true;

    private Config() {
    }

    /**
     * Wired up in {@link MonkeyTail} for both {@code Loading} and {@code Reloading}. Subscribing
     * to the shared parent event does not work on NeoForge 1.21 — it is never fired on its own,
     * so the fields would sit at their defaults forever.
     */
    static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return; // some other config of ours, if there ever is one
        }
        emptyOffhand = EMPTY_OFFHAND.get();
        pressAndHold = PRESS_AND_HOLD.get();
        routeToBackpacks = ROUTE_TO_BACKPACKS.get();
    }
}
