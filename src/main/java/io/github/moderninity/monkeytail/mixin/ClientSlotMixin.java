package io.github.moderninity.monkeytail.mixin;

import io.github.moderninity.monkeytail.ClientKey;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * One of the two "slot 9 is a legal choice" changes, this one on the client.
 *
 * <p>When the server tells the client which slot the player is holding, the client checks
 * {@code Inventory.isHotbarSlot}, which answers false for 9 and quietly ignores the message.
 * We answer that one call ourselves. Every other use of {@code isHotbarSlot} in the game is
 * untouched, because the redirect is scoped to this one method.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientSlotMixin {

    @Redirect(
            method = "handleSetCarriedItem",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/entity/player/Inventory;isHotbarSlot(I)Z"))
    private boolean monkeytail$allowExtraSlot(int slot) {
        return slot >= 0 && slot < Inventory.getSelectionSize() + 1;
    }

    /** Keep track of where the server thinks we are, so the toggle can put us back there. */
    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"))
    private void monkeytail$rememberSlot(ClientboundSetCarriedItemPacket packet, CallbackInfo callback) {
        ClientKey.rememberServerSlot(packet.getSlot());
    }
}
