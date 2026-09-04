package io.github.moderninity.monkeytail.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The other half of "slot 9 is a legal choice", on the server.
 *
 * <p>When the client says it has changed slots, the server rejects anything that is not below
 * {@code Inventory.getSelectionSize()}, which is 9. We answer 10 for that one comparison, and
 * only that one — the scroll wheel, the hotbar keys and everything else still see the real 9.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerSlotMixin {

    @Redirect(
            method = "handleSetCarriedItem",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/entity/player/Inventory;getSelectionSize()I"))
    private int monkeytail$acceptExtraSlot() {
        return Inventory.getSelectionSize() + 1;
    }
}
