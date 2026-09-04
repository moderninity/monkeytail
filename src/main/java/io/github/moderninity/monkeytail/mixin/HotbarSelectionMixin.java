package io.github.moderninity.monkeytail.mixin;

import io.github.moderninity.monkeytail.MonkeyTail;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Puts the "your hands are down" indicator on the left of the hotbar instead of the right.
 *
 * <p>There is still no drawing here and still no texture. All this does is change one number:
 * the x position vanilla passes when it draws its own white selection box. Vanilla puts that box
 * at {@code (screen centre - 92) + selected * 20}, so for slot 9 it lands at
 * {@code centre + 88} — one cell past the right-hand end of the hotbar, half of it hanging off
 * the edge. A cell to the <em>left</em> of the hotbar is {@code (screen centre - 92) - 20},
 * which is exactly 200 pixels further left, whatever the screen size.
 *
 * <p>Why this is not the kind of HUD code the rest of the mod avoids: it draws nothing, adds no
 * render layer and touches no render state, which is where a mod like this normally breaks other
 * people's GUIs. It is also a {@code @ModifyArg} rather than a {@code @Redirect}, so several
 * mods can adjust the same call without fighting over it.
 *
 * <p>{@code require = 0} on purpose. If a HUD mod rewrites {@code renderItemHotbar} enough that
 * the injection no longer fits, the indicator quietly stays on vanilla's right-hand side, which
 * is a far better outcome for a cosmetic touch than refusing to start the game.
 */
@Mixin(Gui.class)
public abstract class HotbarSelectionMixin {

    /** How far left of vanilla's slot-9 position a cell before the hotbar sits. */
    private static final int ONE_CELL_LEFT_OF_HOTBAR = 200;

    @ModifyArg(
            method = "renderItemHotbar",
            at = @At(value = "INVOKE",
                     ordinal = 1,
                     target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"),
            index = 1,
            require = 0)
    private int monkeytail$moveSelectionBoxLeft(int x) {
        // The camera entity rather than the local player, so it still follows whoever you are
        // spectating — the same player vanilla drew the hotbar for.
        if (!(Minecraft.getInstance().getCameraEntity() instanceof Player player)
                || player.getInventory().selected != MonkeyTail.EXTRA_SLOT) {
            return x;
        }

        // Stay on the right if the offhand cell is already sitting on the left. Vanilla draws
        // that cell on the side opposite your main hand, and only when the offhand is holding
        // something — which, with empty_offhand on, it never is while your hands are down. So in
        // the default setup this never triggers and the indicator is always on the left.
        boolean offhandCellIsOnTheLeft = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
        if (offhandCellIsOnTheLeft && !player.getOffhandItem().isEmpty()) {
            return x;
        }

        return x - ONE_CELL_LEFT_OF_HOTBAR;
    }
}
