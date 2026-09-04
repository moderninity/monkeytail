package io.github.moderninity.monkeytail;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;

/**
 * Finds backpacks the player is <em>wearing</em>, wherever they happen to be worn.
 *
 * <p>This is the only class in the mod that names a Sophisticated Backpacks type, so it must
 * only ever be reached from behind the {@code ModList.isLoaded} check in {@link Backpacks}.
 * Java does not load a class until something actually runs that touches it, so when that mod is
 * absent this class is never loaded and nothing breaks.
 *
 * <p>Why go through Sophisticated Backpacks rather than an accessory mod directly: a backpack
 * can be worn in a Curios slot, an Accessories slot, or a slot some future mod invents, and
 * asking any one of those systems finds bags in that system only. Sophisticated Backpacks keeps
 * a registry that every one of those integrations signs itself up to, so a single walk covers
 * the main inventory, offhand, armour and every accessory slot at once.
 *
 * <p>Putting the item in still goes through the ordinary capability rather than that mod's
 * internals, so the backpack's own filters and input settings are respected and a refusal is a
 * legitimate answer, not a bug.
 */
final class SophisticatedWorn {

    private SophisticatedWorn() {
    }

    /** Returns whatever would not fit. */
    static ItemStack insert(Player player, ItemStack stack) {
        // A one-element array because the callback below cannot assign to a local variable.
        ItemStack[] leftover = {stack};

        PlayerInventoryProvider.get().runOnBackpacks(player, (backpack, inventoryName, identifier, slot) -> {
            if (backpack != leftover[0]) {
                IItemHandler contents = backpack.getCapability(Capabilities.ItemHandler.ITEM);
                if (contents != null) {
                    leftover[0] = ItemHandlerHelper.insertItemStacked(contents, leftover[0], false);
                }
            }
            // Returning true stops the walk, so stop as soon as there is nothing left to place.
            return leftover[0].isEmpty();
        });

        return leftover[0];
    }
}
