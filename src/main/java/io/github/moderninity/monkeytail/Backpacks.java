package io.github.moderninity.monkeytail;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The last thing tried before an item hits the ground: push it into a bag the player already
 * has on them.
 *
 * <p>This only ever runs when the extra slot is selected <em>and</em> all 36 inventory slots are
 * full, so nothing about normal play changes.
 *
 * <p>It is deliberately not written against any particular storage mod. Every candidate item is
 * asked for the standard {@code ItemHandler} capability, which is how a container item is
 * supposed to advertise that things can be put inside it, so any well-behaved bag works with no
 * dependency at all. The one exception is finding a bag that is being <em>worn</em> rather than
 * carried, which no vanilla API can do — see {@link SophisticatedWorn}.
 */
public final class Backpacks {

    private static Boolean sophisticatedBackpacksPresent;

    private Backpacks() {
    }

    /**
     * Server side only. Returns true if the whole stack found a home; otherwise shrinks
     * {@code stack} to whatever is left over, which the caller should drop.
     */
    public static boolean stash(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        ItemStack leftover = stack;

        // Worn bags first. They are not in the inventory arrays, so the loop below cannot see
        // them, and a bag on your back is the more natural place for something to go.
        if (hasSophisticatedBackpacks()) {
            try {
                leftover = SophisticatedWorn.insert(player, leftover);
            } catch (Throwable failure) {
                // If that mod moves the class we call, that should cost a log line, not the
                // player's item and not this mod.
                MonkeyTail.LOG.error("Sophisticated Backpacks lookup failed; falling back", failure);
            }
            if (leftover.isEmpty()) {
                return true;
            }
        }

        leftover = insertIntoCarriedBags(player, stack, leftover);
        if (leftover.isEmpty()) {
            return true;
        }

        // Partly stored: leave only the genuine remainder for the caller to drop.
        stack.setCount(leftover.getCount());
        return false;
    }

    /** Walks the inventory, the armour slots and the offhand — {@code Inventory.getItem} covers
     *  all three in one numbering. */
    private static ItemStack insertIntoCarriedBags(Player player, ItemStack original, ItemStack leftover) {
        Inventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getContainerSize() && !leftover.isEmpty(); slot++) {
            if (slot == MonkeyTail.EXTRA_SLOT) {
                continue; // the slot we are keeping empty
            }
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty() || candidate == original || candidate == leftover) {
                continue; // never ask a bag to swallow itself
            }

            IItemHandler bag = candidate.getCapability(Capabilities.ItemHandler.ITEM);
            if (bag != null) {
                leftover = ItemHandlerHelper.insertItemStacked(bag, leftover, false);
            }
        }
        return leftover;
    }

    /** Looked up once. The answer cannot change while the game is running. */
    private static boolean hasSophisticatedBackpacks() {
        Boolean present = sophisticatedBackpacksPresent;
        if (present == null) {
            present = ModList.get().isLoaded("sophisticatedbackpacks");
            sophisticatedBackpacksPresent = present;
        }
        return present;
    }
}
