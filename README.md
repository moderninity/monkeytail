# Monkey Tail

A tenth hotbar slot that can never hold anything. One key puts your hands down.

**NeoForge 21.1 / Minecraft 1.21.1, written from scratch — no upstream code, assets, strings or
version lineage.** Build output: `output/monkeytail-neoforge-1.21.1-1.0.0.jar`.

## Why it exists

`Hold My Beer: Empty Hand` (ItsWormy / littlebroto1, CurseForge project 872312) does this on
Forge 1.20.1 and was last updated 30 June 2024. Its author has not been reachable for over a
year, so permission to reuse the code or the hands icon could not be obtained. This is an
independent implementation of the same idea. Nothing from that mod ships here: different package,
different mod id, different name, different class and translation keys, no textures, and a
version number that starts at 1.0.0 rather than continuing his 1.2.3.

The one thing that is genuinely the same is the *approach*, and it could not be otherwise —
Minecraft has exactly two places that reject a hotbar slot above 8, and any mod doing this has to
widen those same two checks. That is the game's API, not anyone's authorship.

## The idea, and why it does not fight with other mods

The extra slot is not drawn on, faked, or bolted to the side of the hotbar. It is **inventory
slot 9** — the first slot of the main 3x9 grid, the leftmost one on the bottom row of the
inventory screen — which the game already stores immediately after the nine hotbar slots.

That single decision is what makes it well behaved:

- **Hotbar and HUD mods never see it.** The hotbar is still nine slots, still the same width,
  still in the same place. Nothing is inserted, nothing is renumbered.
- **Inventory-sorting and storage mods never see it.** It is an ordinary inventory slot that
  happens to be kept empty.
- **Mods that want an empty hand just work.** They ask the player what is in their hand; while
  the slot is selected the answer is "nothing", through the same method call they always use.
  There is no whitelist and no per-mod compatibility to maintain.

Two mixins widen exactly two bounds checks — the client's `Inventory.isHotbarSlot` in
`ClientPacketListener.handleSetCarriedItem`, and the server's `Inventory.getSelectionSize`
comparison in `ServerGamePacketListenerImpl.handleSetCarriedItem` — and both are scoped to that
one method, so the scroll wheel, the 1-9 keys and every other user of those methods still see the
real nine.

## Blocked off in both directions

A third mixin, on `Player`, is what makes the slot genuinely refuse items rather than merely
usually be empty:

- **Nothing gets in.** Anything that puts an item in your hand ends up calling
  `Player.setItemSlot`. While the slot is selected, that item goes to your inventory instead.
- **Nothing reads out.** While the slot is selected, `Player.getItemBySlot` reports the hand as
  empty. So even if something did reach slot 9 — you dragged it there in the inventory screen, or
  a pickup filled the last free space — you still cannot hold it.

The second half is the part upstream did not have, and it is what makes "completely blocked off"
true rather than nearly true.

Where a diverted item goes, in order: the first free inventory slot **that is not slot 9**, then
a bag you are carrying or wearing (optional, see below), then the ground. Skipping slot 9
matters: vanilla's own `Inventory.getFreeSlot` scans in order and returns 9 the moment the hotbar
is full, which would fill the slot that is supposed to stay empty *and* silently overwrite
whatever was already in it.

## The indicator: no texture, one integer

This mod draws nothing and ships no textures. The indicator is vanilla's own white selection
box, moved.

Vanilla puts that box at `(screen centre - 92) + selected * 20`. For slot 9 that lands at
`centre + 88` — one cell past the *right* end of the hotbar, with half of it hanging off the
edge. `HotbarSelectionMixin` changes that one x argument so the box sits one cell to the **left**
of the hotbar instead, which is exactly 200 pixels further left at any screen size.

That is the whole HUD change: no render layer, no texture, no render state touched. It is also a
`@ModifyArg` rather than a `@Redirect`, so other mods can adjust the same draw call without
fighting over it, and it carries `require = 0` — if a HUD mod reshapes `renderItemHotbar` beyond
recognition the box quietly stays on vanilla's right-hand side rather than the game refusing to
start over a cosmetic touch.

**The left side is normally free precisely when the indicator shows.** Vanilla draws the offhand
cell on the side opposite your main hand — the left, for a right-handed player — but only when
the offhand is actually holding something. With `empty_offhand` on (the default) it never is
while your hands are down, so the cell is not drawn and the space is empty. If you turn
`empty_offhand` off and carry a shield, the mixin notices and leaves the indicator on the right
rather than drawing the two on top of each other.

This is worth contrasting with what upstream did, because it is the same feature done two very
different ways. Upstream registered a GUI overlay that drew its own hands texture with its own
render state; of its last three releases, two were fixes for that (v1.1.1 "compatibility with the
Raised mod", v1.2.3 "fixed an issue when rendering some GUIs (e.g. ones from the Mekanism mod)"),
plus a selection-icon clipping fix in v1.1.3. Changing one integer cannot produce any of those.

## Keybind

Default is **`` ` `` / `~`** (GLFW grave accent), under its own **Monkey Tail** category in the
Controls screen rather than buried in a vanilla category — in a pack with several hundred
bindings a vanilla category is a haystack. `~` is unbound across all 501 bindings in this
instance's `options.txt` and appears in no mod config.

Mouse buttons work too. The binding is matched with `KeyMapping.matches` / `matchesMouse` rather
than by comparing raw input codes, so a key and a mouse button with the same numeric value cannot
be confused for each other. (Upstream needed a release, v1.1.3, to fix exactly that.)

**Tap mode (default)** puts your hands down; tapping again gives you your item back. Upstream's
key only ever selected the slot — you had to press 1-9 to get out. **Press-and-hold mode** keeps
your hands down only while the key is held. In either mode, pressing 1-9 or scrolling while your
hands are down does what you would expect and is not blocked: if you asked to go to slot 3, you
go to slot 3, and releasing the key does not yank you back.

Nothing happens while a screen is open, so typing a `~` into chat or onto a sign does not move
your hand.

## Settings

`config/monkeytail-client.toml`:

| key | default | effect |
|---|---|---|
| `empty_offhand` | `true` | your offhand reads as empty too, so a shield stops rendering and stops blocking |
| `press_and_hold` | `false` | hold the key instead of tapping it |
| `route_to_backpacks` | `true` | when the inventory is completely full, fill a bag rather than dropping the item |

`empty_offhand` and `route_to_backpacks` are consulted on the **server**, inside the `Player`
mixin, and a client config file does not exist there. The client therefore sends both values once
as it joins (`Prefs`), and the server remembers them per player until that player logs out.
Reading the client config directly on the server would silently give you the field defaults —
that is the "config sync issue" upstream fixed in v1.1.4, and the reason this is a payload rather
than a shortcut.

The network channel is **required on both sides**: the server half does the actual work, so a
client-only install would be broken anyway, and NeoForge refusing the connection is a clearer
failure than a mod that half works.

## Backpacks

Optional, and only ever reached when the extra slot is selected *and* all 36 inventory slots are
full — so nothing about normal play changes until the point where the item would have been lost.

`Backpacks` is mod-agnostic: every candidate is asked for the standard
`Capabilities.ItemHandler.ITEM`, which is how a container item is supposed to advertise that
things go inside it, so any well-behaved bag works with no dependency at all.

`SophisticatedWorn` solves the one thing no vanilla API can: finding a bag that is being **worn**.
This pack runs Curios and Accessories *at the same time* (`curios-neoforge-9.5.1`,
`accessories-neoforge-1.1.0-beta.53`, plus `accessorify`), and asking either one directly finds
bags in that system only while silently missing the other. Sophisticated Backpacks keeps a
`PlayerInventoryProvider` that both integrations register themselves with, so one walk covers the
main inventory, offhand, armour, Curios slots and Accessories slots — and any future integration
for free. SB is used purely as the *locator*; the insert still goes through the ordinary
capability, so the bag's own filters and IO settings are respected.

It is a genuinely optional dependency, and that is verified rather than assumed — see below.

## Layout

```
build.sh                 offline build, no Gradle, no network
src/main/java/           10 source files, no generated code
src/main/resources/      neoforge.mods.toml, mixin config, lang
build/                   classes and classpath lists (generated)
output/                  the built jar
```

| file | what it is |
|---|---|
| `MonkeyTail.java` | the mod entry point, and the one place every registration is wired up |
| `Config.java` | the three settings, cached into plain fields because one is read thousands of times a second |
| `Prefs.java` | the two settings the server needs, the message that carries them, and the per-player map |
| `ClientKey.java` | the keybind and what pressing it does — selecting the slot is one assignment |
| `Backpacks.java` | the inventory-full fallback, written against no particular storage mod |
| `SophisticatedWorn.java` | the only class that names a Sophisticated Backpacks type |
| `mixin/PlayerMixin.java` | blocks the slot off in both directions |
| `mixin/HotbarSelectionMixin.java` | moves vanilla's selection box to the left of the hotbar |
| `mixin/ClientSlotMixin.java` | lets the client accept slot 9 from the server |
| `mixin/ServerSlotMixin.java` | lets the server accept slot 9 from the client |

## Building

```bash
./build.sh
```

Compiles against **NeoForge 21.1.233** — deliberately the oldest 21.1 in the Prism library store,
not the newest. Building against an old API and running on a newer one is safe; the reverse is
not. The instance runs 21.1.249. Override with `$NEOFORGE_VERSION`, `$JDK`, `$PRISM_LIBS`,
`$MODS_DIR`, `$NFRT_CACHE` and `$PORT_TOOLS`. The two static checks below live outside this
repository; if `port-tools/` is not beside it the jar still builds and they are skipped.

## Verification

- compiles clean against MC 1.21.1 + NeoForge 21.1.233
- `port-tools/eventbus_check.py`: 12 classes, **0 problems** — catches a listener registration
  that compiles fine and then kills mod loading
- `port-tools/linkcheck.py`: 12 classes, 78 distinct class refs, **0 unresolved**
- **optional dependency is real**: rerun with the Sophisticated Backpacks jars stripped from the
  classpath and there are exactly 2 unresolved refs, both `PlayerInventoryProvider`, both inside
  `SophisticatedWorn` — and no other class in the jar mentions `p3pp3rf1y` anywhere
- **dist safety**: only `ClientKey*`, `ClientSlotMixin` and `HotbarSelectionMixin` reference
  `net/minecraft/client`; both mixins are in the `client` list of the mixin config and
  `ClientKey` is reachable only through `Dist.CLIENT` subscribers. This is structurally the bug upstream shipped as
  "servers crash on boot" in v1.2.2
- all four injection points confirmed in disassembled 1.21.1 bytecode: `Player.setItemSlot`,
  `Player.getItemBySlot`, the `Inventory.isHotbarSlot` call in
  `ClientPacketListener.handleSetCarriedItem`, and the `Inventory.getSelectionSize` call in
  `ServerGamePacketListenerImpl.handleSetCarriedItem`. The indicator's target is ordinal 1 of the
  four `GuiGraphics.blitSprite(ResourceLocation,IIII)` calls in `Gui.renderItemHotbar`, confirmed
  as `HOTBAR_SELECTION_SPRITE` in the disassembly

**Not launched in game yet.**

## Installing

```
cp output/monkeytail-neoforge-1.21.1-1.0.0.jar \
   "/d/Games/Modding/PrismLauncher/instances/Cosmic Ambition 1211/minecraft/mods/"
rm "/d/Games/Modding/PrismLauncher/instances/Cosmic Ambition 1211/minecraft/mods/holdmybeer-neoforge-1.21.1-1.2.3.jar"
```

The old jar **must** come out. The mod ids differ (`monkeytail` vs `holdmybeer`), so FML will
happily load both, and both will fight over inventory slot 9.

## Known limits, deliberate

- Nothing stops you dragging an item into slot 9 in the inventory screen, and a pickup can still
  land there when everything else is full. Neither lets you hold it — the hand still reports
  empty — but the item is then sitting in a slot the mod calls empty. Blocking placement outright
  needs a mixin on `Slot.mayPlace`, and blocking pickups needs one on `Inventory.getFreeSlot`,
  which would change vanilla behaviour for every caller in the game. Not worth it.
- No position or appearance settings. The indicator is vanilla's own sprite in a fixed place;
  moving it further, or back to the right, is changing one constant in `HotbarSelectionMixin`.
