package io.github.moderninity.monkeytail;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The two settings the server half of the mod has to know about, and the message that carries
 * them there.
 *
 * <p>Why this exists at all: the code that keeps the slot empty lives in a mixin on
 * {@code Player}, and that runs on the server. {@link Config} is a client file, so the server
 * simply cannot read it. The client therefore posts its answers once, when it joins, and the
 * server remembers them per player until that player logs out.
 *
 * <p>Use {@link #emptyOffhand(Player)} and {@link #routeToBackpacks(Player)} rather than
 * touching either source directly — they pick the right one for whichever side you are on.
 */
public record Prefs(boolean emptyOffhand, boolean routeToBackpacks) implements CustomPacketPayload {

    public static final Type<Prefs> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MonkeyTail.ID, "prefs"));

    /** Two booleans on the wire, in the order they appear in the record. */
    public static final StreamCodec<FriendlyByteBuf, Prefs> STREAM_CODEC = StreamCodec.of(
            (buf, prefs) -> {
                buf.writeBoolean(prefs.emptyOffhand());
                buf.writeBoolean(prefs.routeToBackpacks());
            },
            buf -> new Prefs(buf.readBoolean(), buf.readBoolean()));

    /** What a player gets before their message arrives, and if it never does. */
    private static final Prefs DEFAULTS = new Prefs(true, true);

    /**
     * Server side only. Written from the network thread and read from the server thread, which
     * is why it is a concurrent map rather than a plain one.
     */
    private static final Map<UUID, Prefs> BY_PLAYER = new ConcurrentHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Called on the server when a client's message arrives. */
    static void receive(Prefs prefs, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BY_PLAYER.put(player.getUUID(), prefs);
            }
        });
    }

    /** Don't hold on to settings for players who have gone. */
    static void onPlayerLeft(PlayerEvent.PlayerLoggedOutEvent event) {
        BY_PLAYER.remove(event.getEntity().getUUID());
    }

    public static boolean emptyOffhand(Player player) {
        return player.level().isClientSide() ? Config.emptyOffhand : forServer(player).emptyOffhand();
    }

    public static boolean routeToBackpacks(Player player) {
        return player.level().isClientSide() ? Config.routeToBackpacks : forServer(player).routeToBackpacks();
    }

    private static Prefs forServer(Player player) {
        return BY_PLAYER.getOrDefault(player.getUUID(), DEFAULTS);
    }
}
