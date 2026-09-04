package io.github.moderninity.monkeytail;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Everything on the client: the key you press, and what pressing it does.
 *
 * <p>Selecting the slot is literally one assignment — {@code inventory.selected = 9}. The client
 * notices the change on its next tick and tells the server for us, the same way it does when you
 * press 1-9 or scroll the wheel. The two mixins are only there to stop the client and the server
 * rejecting a slot number they think is out of range.
 */
public final class ClientKey {

    /** Its own Controls category, because a vanilla one is a haystack in a big pack. */
    public static final String CATEGORY = "key.categories.monkeytail";
    public static final String NAME = "key.monkeytail.empty_hands";

    /** The slot you were on before, so tapping the key again gives you your item back. */
    private static int previousSlot = 0;

    private static KeyMapping key;

    private ClientKey() {
    }

    /** Registered during startup. */
    @EventBusSubscriber(modid = MonkeyTail.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static final class Setup {
        @SubscribeEvent
        static void registerKeys(RegisterKeyMappingsEvent event) {
            // GLFW_KEY_GRAVE_ACCENT is the ` / ~ key under Esc. Nothing in vanilla uses it, so
            // it is a safe default even in a pack with hundreds of bindings.
            key = new KeyMapping(NAME, GLFW.GLFW_KEY_GRAVE_ACCENT, CATEGORY);
            event.register(key);
        }
    }

    /** Listened to while you play. */
    @EventBusSubscriber(modid = MonkeyTail.ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    static final class Input {

        @SubscribeEvent
        static void onKey(InputEvent.Key event) {
            if (key != null && key.matches(event.getKey(), event.getScanCode())) {
                onBindingChanged(event.getAction());
            }
        }

        /** Same handling again, in case the key has been rebound to a mouse button. */
        @SubscribeEvent
        static void onMouseButton(InputEvent.MouseButton.Pre event) {
            if (key != null && key.matchesMouse(event.getButton())) {
                onBindingChanged(event.getAction());
            }
        }

        /**
         * Tell the server the two settings it cannot read for itself, once, as we join.
         * See {@link Prefs}.
         */
        @SubscribeEvent
        static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
            PacketDistributor.sendToServer(new Prefs(Config.emptyOffhand, Config.routeToBackpacks));
        }
    }

    /** {@code action} is one of GLFW's PRESS / RELEASE / REPEAT. */
    private static void onBindingChanged(int action) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        // Not while a screen is open, or typing a ~ into chat would move your hand.
        if (player == null || minecraft.screen != null) {
            return;
        }

        Inventory inventory = player.getInventory();
        boolean handsAreDown = inventory.selected == MonkeyTail.EXTRA_SLOT;

        if (Config.pressAndHold) {
            if (action == GLFW.GLFW_PRESS) {
                previousSlot = inventory.selected;
                inventory.selected = MonkeyTail.EXTRA_SLOT;
            } else if (action == GLFW.GLFW_RELEASE && handsAreDown) {
                // Only put the old item back if you are still on the extra slot: if you pressed
                // 3 or scrolled while holding the key, you meant to go there.
                inventory.selected = previousSlot;
            }
        } else if (action == GLFW.GLFW_PRESS) {
            if (handsAreDown) {
                inventory.selected = previousSlot;
            } else {
                previousSlot = inventory.selected;
                inventory.selected = MonkeyTail.EXTRA_SLOT;
            }
        }

        // We acted on the raw input, so drop the click vanilla queued up for this mapping.
        while (key.consumeClick()) {
            // nothing to do, just draining
        }
    }

    /**
     * Called from the client mixin when the server moves us: keep {@link #previousSlot} honest
     * so tapping the key still returns you to where the server thinks you are.
     */
    public static void rememberServerSlot(int slot) {
        if (slot != MonkeyTail.EXTRA_SLOT) {
            previousSlot = slot;
        }
    }
}
