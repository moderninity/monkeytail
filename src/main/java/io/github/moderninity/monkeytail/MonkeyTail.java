package io.github.moderninity.monkeytail;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/**
 * Monkey Tail — a tenth hotbar slot that can never hold anything, so one key puts your hands
 * down instead of you having to find somewhere to stash what you were holding first.
 *
 * <p>How the whole thing works:
 * <ul>
 *   <li>The "tenth slot" is not a drawn-on extra. It is inventory slot 9 — the first slot of the
 *       main 3x9 grid — which the game already stores right next to the hotbar. That is the
 *       whole reason this plays nicely with other mods: to a hotbar mod, a HUD mod or an
 *       inventory mod, the hotbar is still nine slots and nothing has moved.</li>
 *   <li>Minecraft refuses to let you select a slot above 8, once on the client and once on the
 *       server. Two small mixins widen exactly those two checks, and nothing else.</li>
 *   <li>A mixin on {@code Player} then blocks the slot off in both directions: nothing can be
 *       put in your hand while it is selected, and your hand reports as empty whatever happens
 *       to be sitting in the slot. So every mod that wants a free hand to interact — and there
 *       are a lot of them — simply sees a free hand. See {@code PlayerMixin}.</li>
 *   <li>Two of the settings are consulted on the server, where a client config file does not
 *       exist, so the client posts them once when it joins. See {@link Prefs}.</li>
 * </ul>
 *
 * <p>There is deliberately nothing drawn and no texture shipped. The indicator is vanilla's own
 * white selection box: when slot 9 is selected it would land one cell past the right-hand end of
 * the hotbar, and {@code HotbarSelectionMixin} nudges that one x coordinate so it sits one cell
 * to the left instead. Shipping a texture and a HUD layer is where a mod like this usually
 * breaks against other people's GUIs, so it does neither.
 */
@Mod(MonkeyTail.ID)
public final class MonkeyTail {

    public static final String ID = "monkeytail";
    public static final Logger LOG = LogUtils.getLogger();

    /**
     * The slot this mod hands you. Inventory slots 0-8 are the hotbar, so 9 is the first slot
     * of the grid above it — the leftmost one on the bottom row of the inventory screen.
     */
    public static final int EXTRA_SLOT = 9;

    /**
     * The mod's own network channel version. Bumping this string stops an old client from
     * connecting to a new server; there is no reason to touch it unless {@link Prefs} changes
     * what it sends.
     */
    private static final String CHANNEL_VERSION = "1";

    /**
     * NeoForge builds this with the mod's own event bus and its container. Everything the mod
     * needs to register is wired up here rather than through annotations, so there is one place
     * to read to see all of it.
     */
    public MonkeyTail(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        // Mod bus: fired once each, during startup, for this mod only.
        modBus.addListener(RegisterPayloadHandlersEvent.class, MonkeyTail::registerNetwork);
        modBus.addListener(ModConfigEvent.Loading.class, Config::onConfigChanged);
        modBus.addListener(ModConfigEvent.Reloading.class, Config::onConfigChanged);

        // Game bus: fired repeatedly, during play, for every mod.
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, Prefs::onPlayerLeft);
    }

    private static void registerNetwork(RegisterPayloadHandlersEvent event) {
        // A plain (non-optional) registrar means NeoForge will not let a client connect unless
        // the server also has this mod. That is what we want: the server half does the actual
        // work of keeping the slot empty, so a client-only install would be broken anyway.
        event.registrar(CHANNEL_VERSION).playToServer(Prefs.TYPE, Prefs.STREAM_CODEC, Prefs::receive);
    }
}
