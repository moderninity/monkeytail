package io.github.moderninity.monkeytail.mixin;

import io.github.moderninity.monkeytail.Backpacks;
import io.github.moderninity.monkeytail.MonkeyTail;
import io.github.moderninity.monkeytail.Prefs;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The part that makes the slot refuse to hold anything.
 *
 * <p>There are two halves to it, and both are needed for the slot to be genuinely blocked off
 * rather than merely usually empty:
 *
 * <ol>
 *   <li><b>Nothing gets put in.</b> Plenty of things can put an item in your hand — a dispenser,
 *       an equip button, another mod. Rather than trying to find all of them, we intercept the
 *       one method they all end up calling, {@code Player.setItemSlot}, and while the extra slot
 *       is selected we put the item in the inventory instead and tell the caller we are done.</li>
 *   <li><b>Nothing reads out.</b> While the extra slot is selected, the hand simply reports as
 *       empty. So even if something did find its way into slot 9 — you dragged it there in the
 *       inventory screen, or a pickup filled the last free space — you still cannot hold it, and
 *       every mod that asks "is your hand empty?" gets the same honest answer.</li>
 * </ol>
 *
 * <p>This runs on both the client and the server. Only the server may make a decision that
 * changes the world (dropping an item, filling a backpack); the client just declines to hold the
 * item and waits to be told what happened, otherwise you get a ghost item on the floor that is
 * not really there.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true)
    private void monkeytail$keepHandsEmpty(EquipmentSlot slot, ItemStack stack, CallbackInfo callback) {
        Player player = monkeytail$self();
        Inventory inventory = player.getInventory();

        if (inventory.selected != MonkeyTail.EXTRA_SLOT) {
            return;
        }
        boolean isAHand = slot == EquipmentSlot.MAINHAND
                || (slot == EquipmentSlot.OFFHAND && Prefs.emptyOffhand(player));
        if (!isAHand) {
            return;
        }

        int free = monkeytail$firstFreeSlot(inventory);
        if (free != -1) {
            inventory.setItem(free, stack);
        } else if (!player.level().isClientSide()) {
            boolean stored = Prefs.routeToBackpacks(player) && Backpacks.stash(player, stack);
            if (!stored) {
                player.drop(stack, false, false);
            }
        }

        callback.cancel();
    }

    /** Report an empty hand while the extra slot is selected. */
    @Inject(method = "getItemBySlot", at = @At("HEAD"), cancellable = true)
    private void monkeytail$emptyHands(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> callback) {
        // Cheapest test first: this method is called thousands of times a second.
        if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) {
            return;
        }
        Player player = monkeytail$self();
        if (player.getInventory().selected != MonkeyTail.EXTRA_SLOT) {
            return;
        }
        // The offhand is only blanked if you asked for it; a shield or torch can stay out.
        if (slot == EquipmentSlot.MAINHAND || Prefs.emptyOffhand(player)) {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }

    /**
     * The first empty inventory slot that is not the extra slot itself.
     *
     * <p>Vanilla's own {@code Inventory.getFreeSlot} scans in order and so returns slot 9 the
     * moment the hotbar is full — which would both fill the slot that is supposed to stay empty
     * and overwrite whatever was already sitting in it. We skip it and carry on up the
     * inventory; if there is genuinely nowhere left, the caller falls back to a bag, then to
     * dropping the item.
     */
    @Unique
    private int monkeytail$firstFreeSlot(Inventory inventory) {
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (slot != MonkeyTail.EXTRA_SLOT && inventory.items.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** This mixin is woven into Player, so {@code this} really is one. */
    @Unique
    private Player monkeytail$self() {
        return (Player) (Object) this;
    }
}
