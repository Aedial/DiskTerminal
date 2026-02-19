package com.cellterminal.integration;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.api.features.IWirelessTermHandler;
import appeng.client.gui.AEBaseGui;
import appeng.container.AEBaseContainer;
import appeng.helpers.WirelessTerminalGuiObject;

import com.cellterminal.CellTerminal;
import com.cellterminal.ItemRegistry;
import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.container.ContainerWUTCellTerminal;
import com.cellterminal.gui.GuiWUTCellTerminal;
import com.circulation.ae2wut.AE2UELWirelessUniversalTerminal;
import com.circulation.ae2wut.client.model.ItemWUTBakedModel;
import com.circulation.ae2wut.item.ItemWirelessUniversalTerminal;
import com.circulation.ae2wut.recipes.AllWUTRecipe;


/**
 * Integration with AE2UEL Wireless Universal Terminal (AE2WUT).
 * Allows Cell Terminal to be used as a mode in the Wireless Universal Terminal.
 *
 * NOTE: This integration comes with 2 caveats due to limitations in AE2WUT:
 * 1. The modes **must** be contiguous starting from 0 up to N with no gaps.
 *    If other mods add modes to the WUT, Cell Terminal's mode ID may conflict.
 *    The mode ID can be configured in the server config (requires restart).
 * 2. Due to using a static atlas for WUT icons, the Cell Terminal icon
 *    will not render on other modes' buttons unless those modes also
 *    register their icons in the same way. This is a limitation of AE2WUT's
 *    current design.
 */
public class AE2WUTIntegration {

    private static final String MODID = "ae2wut";
    private static Boolean modLoaded = null;

    /**
     * Get the mode ID for Cell Terminal in WUT.
     * This value is read from server config and requires a restart to change.
     */
    public static byte getCellTerminalMode() {
        return CellTerminalServerConfig.getInstance().getWutModeId();
    }

    /**
     * Check if AE2WUT is loaded.
     */
    public static boolean isModLoaded() {
        if (modLoaded == null) {
            boolean loaded = Loader.isModLoaded(MODID);
            if (loaded && CellTerminalServerConfig.isInitialized()) {
                loaded = CellTerminalServerConfig.getInstance().isIntegrationAE2WUTEnabled();
            }

            modLoaded = loaded;
        }

        return modLoaded;
    }

    /**
     * Register Cell Terminal with AE2WUT's container system.
     * Called during init phase.
     */
    public static void registerContainer() {
        if (!isModLoaded()) return;

        registerContainerInternal();
    }

    @Optional.Method(modid = MODID)
    private static void registerContainerInternal() {
        AE2UELWirelessUniversalTerminal.instance.registryContainer(
            getCellTerminalMode(),
            (item, player, slot, isBauble) -> {
                WirelessTerminalGuiObject wth = getWirelessTerminalGuiObject(item, player, slot, isBauble);
                if (wth == null) return null;

                return new ContainerWUTCellTerminal(player.inventory, wth);
            }
        );
    }

    /**
     * Register Cell Terminal with AE2WUT's GUI system.
     * Called during init phase on client side.
     */
    @SideOnly(Side.CLIENT)
    public static void registerGui() {
        if (!isModLoaded()) return;

        registerGuiInternal();
    }

    @SideOnly(Side.CLIENT)
    @Optional.Method(modid = MODID)
    private static void registerGuiInternal() {
        AE2UELWirelessUniversalTerminal.instance.registryGui(
            getCellTerminalMode(),
            (item, player, slot, isBauble) -> {
                WirelessTerminalGuiObject wth = getWirelessTerminalGuiObject(item, player, slot, isBauble);
                if (wth == null) return null;

                return new GuiWUTCellTerminal(player.inventory, wth);
            }
        );
    }

    /**
     * Register Cell Terminal icon and recipe ingredient with AE2WUT.
     * Called during postInit phase.
     */
    public static void registerRecipeIngredient() {
        if (!isModLoaded()) return;

        registerRecipeIngredientInternal();
    }

    @Optional.Method(modid = MODID)
    private static void registerRecipeIngredientInternal() {
        // Add to the ingredient list so WUT recognizes our terminal in crafting
        ItemStack ingredient = new ItemStack(ItemRegistry.WIRELESS_CELL_TERMINAL);
        AllWUTRecipe.itemList.put((int) getCellTerminalMode(), ingredient);
    }

    /**
     * Register Cell Terminal icon for WUT's model system.
     * Called during client init phase.
     */
    @SideOnly(Side.CLIENT)
    public static void registerIcon() {
        if (!isModLoaded()) return;

        registerIconInternal();
    }

    @SideOnly(Side.CLIENT)
    @Optional.Method(modid = MODID)
    private static void registerIconInternal() {
        // Use the wireless terminal item for icon registration
        // The part item model doesn't render correctly in WUT's icon system
        ItemStack iconStack = new ItemStack(ItemRegistry.WIRELESS_CELL_TERMINAL);
        if (iconStack.isEmpty()) return;

        ItemWUTBakedModel.regIcon(getCellTerminalMode(), iconStack);
    }

    /**
     * Check if the given ItemStack is a Wireless Universal Terminal.
     */
    public static boolean isWirelessUniversalTerminal(ItemStack stack) {
        if (!isModLoaded()) return false;

        return isWirelessUniversalTerminalInternal(stack);
    }

    @Optional.Method(modid = MODID)
    private static boolean isWirelessUniversalTerminalInternal(ItemStack stack) {
        return stack.getItem() instanceof ItemWirelessUniversalTerminal;
    }

    /**
     * Get the available modes from a WUT item.
     * Returns null if not a WUT or no modes available.
     */
    public static int[] getWUTModes(ItemStack stack) {
        if (!isModLoaded()) return null;

        return getWUTModesInternal(stack);
    }

    @Optional.Method(modid = MODID)
    private static int[] getWUTModesInternal(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemWirelessUniversalTerminal)) return null;
        if (!stack.hasTagCompound()) return null;
        if (!stack.getTagCompound().hasKey("modes", Constants.NBT.TAG_INT_ARRAY)) return null;

        return stack.getTagCompound().getIntArray("modes");
    }

    /**
     * Get the current mode of a WUT item.
     */
    public static byte getWUTCurrentMode(ItemStack stack) {
        if (!isModLoaded()) return 0;

        return getWUTCurrentModeInternal(stack);
    }

    @Optional.Method(modid = MODID)
    private static byte getWUTCurrentModeInternal(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemWirelessUniversalTerminal)) return 0;
        if (!stack.hasTagCompound()) return 0;

        return stack.getTagCompound().getByte("mode");
    }

    /**
     * Open a different terminal mode in the WUT.
     * Called when the user clicks a mode switching button.
     */
    @SideOnly(Side.CLIENT)
    public static void openWUTMode(WirelessTerminalGuiObject obj, byte mode) {
        if (!isModLoaded()) return;

        openWUTModeInternal(obj, mode);
    }

    @SideOnly(Side.CLIENT)
    @Optional.Method(modid = MODID)
    private static void openWUTModeInternal(WirelessTerminalGuiObject obj, byte mode) {
        AE2UELWirelessUniversalTerminal.openWirelessTerminalGui(obj, mode);
    }

    /**
     * Get the icon ItemStack for a given WUT mode.
     */
    @SideOnly(Side.CLIENT)
    public static ItemStack getWUTModeIcon(byte mode) {
        if (!isModLoaded()) return ItemStack.EMPTY;

        return getWUTModeIconInternal(mode);
    }

    @SideOnly(Side.CLIENT)
    @Optional.Method(modid = MODID)
    private static ItemStack getWUTModeIconInternal(byte mode) {
        // Use AllWUTRecipe.itemList which has the actual terminal ItemStacks for all modes
        ItemStack icon = AllWUTRecipe.itemList.get((int) mode);

        return icon != null ? icon : ItemStack.EMPTY;
    }

    @Optional.Method(modid = MODID)
    private static WirelessTerminalGuiObject getWirelessTerminalGuiObject(ItemStack item, EntityPlayer player, int slot, int isBauble) {
        if (item.isEmpty()) return null;

        IWirelessTermHandler wh = AEApi.instance().registries().wireless().getWirelessTerminalHandler(item);
        if (wh == null) return null;

        return new WirelessTerminalGuiObject(wh, item, player, player.world, slot, isBauble, Integer.MIN_VALUE);
    }

    /**
     * Try to open the Cell Terminal mode from a Wireless Universal Terminal.
     * Called by the keybind packet handler to open the WUT in Cell Terminal mode.
     *
     * @param is The ItemStack to check
     * @param player The player
     * @param slot The inventory slot
     * @param isBauble Whether the item is in a baubles slot
     * @return true if the WUT was opened (or an error message was sent)
     */
    public static boolean tryOpenWUTCellTerminal(ItemStack is, EntityPlayer player, int slot, boolean isBauble) {
        if (!isModLoaded()) return false;

        return tryOpenWUTCellTerminalInternal(is, player, slot, isBauble);
    }

    @Optional.Method(modid = MODID)
    private static boolean tryOpenWUTCellTerminalInternal(ItemStack is, EntityPlayer player, int slot, boolean isBauble) {
        if (!(is.getItem() instanceof ItemWirelessUniversalTerminal)) return false;
        if (!is.hasTagCompound()) return false;

        // Check if Cell Terminal mode is available in this WUT
        int[] modes = is.getTagCompound().hasKey("modes", Constants.NBT.TAG_INT_ARRAY)
            ? is.getTagCompound().getIntArray("modes") : null;
        if (modes == null) return false;

        byte cellMode = getCellTerminalMode();
        boolean hasMode = false;
        for (int mode : modes) {
            if (mode == cellMode) {
                hasMode = true;
                break;
            }
        }

        if (!hasMode) return false;

        AE2UELWirelessUniversalTerminal.openWirelessTerminalGui(is, player, cellMode, slot, isBauble);
        return true;
    }
}
