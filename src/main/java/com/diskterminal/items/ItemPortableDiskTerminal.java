package com.diskterminal.items;

import java.util.List;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.util.IConfigManager;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.items.tools.powered.powersink.AEBasePoweredItem;
import appeng.util.ConfigManager;
import appeng.util.Platform;

import baubles.api.BaubleType;
import baubles.api.IBauble;

import appeng.core.CreativeTab;

import com.diskterminal.Tags;
import com.diskterminal.gui.GuiHandler;


/**
 * Portable wireless version of the Disk Terminal.
 * Extends AEBasePoweredItem to have battery, and implements IWirelessTermHandler
 * to work with AE2's wireless infrastructure (for linking with Security Terminal).
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "baubles")
public class ItemPortableDiskTerminal extends AEBasePoweredItem implements IWirelessTermHandler, IBauble {

    public ItemPortableDiskTerminal() {
        super(AEConfig.instance().getWirelessTerminalBattery());
        this.setRegistryName(Tags.MODID, "portable_disk_terminal");
        this.setTranslationKey(Tags.MODID + ".portable_disk_terminal");
        this.setCreativeTab(CreativeTab.instance);
        this.setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack item = player.getHeldItem(hand);

        if (!world.isRemote) {
            // Check if linked
            String encKey = getEncryptionKey(item);
            if (encKey == null || encKey.isEmpty()) {
                player.sendMessage(PlayerMessages.DeviceNotLinked.get());

                return new ActionResult<>(EnumActionResult.FAIL, item);
            }

            // Check if security station exists
            try {
                long parsedKey = Long.parseLong(encKey);
                ILocatable securityStation = AEApi.instance().registries().locatable().getLocatableBy(parsedKey);

                if (securityStation == null) {
                    player.sendMessage(PlayerMessages.StationCanNotBeLocated.get());

                    return new ActionResult<>(EnumActionResult.FAIL, item);
                }
            } catch (NumberFormatException e) {
                player.sendMessage(PlayerMessages.DeviceNotLinked.get());

                return new ActionResult<>(EnumActionResult.FAIL, item);
            }

            // Check power
            if (!hasPower(player, 0.5, item)) {
                player.sendMessage(PlayerMessages.DeviceNotPowered.get());

                return new ActionResult<>(EnumActionResult.FAIL, item);
            }

            // Open our custom GUI
            int slot = hand == EnumHand.MAIN_HAND ? player.inventory.currentItem : 40;
            GuiHandler.openPortableDiskTerminalGui(player, slot, false);
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, item);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addCheckedInformation(ItemStack stack, World world, List<String> lines, ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);

        if (stack.hasTagCompound()) {
            NBTTagCompound tag = Platform.openNbtData(stack);
            String encKey = tag.getString("encryptionKey");

            if (encKey == null || encKey.isEmpty()) {
                lines.add(TextFormatting.RED + GuiText.Unlinked.getLocal());
            } else {
                lines.add(TextFormatting.GREEN + GuiText.Linked.getLocal());
            }
        } else {
            lines.add(TextFormatting.RED + GuiText.Unlinked.getLocal());
        }
    }

    @Override
    public boolean canHandle(ItemStack is) {
        return is.getItem() == this;
    }

    @Override
    public boolean usePower(EntityPlayer player, double amount, ItemStack is) {
        return this.extractAEPower(is, amount, Actionable.MODULATE) >= amount - 0.5;
    }

    @Override
    public boolean hasPower(EntityPlayer player, double amt, ItemStack is) {
        return this.getAECurrentPower(is) >= amt;
    }

    @Override
    public IConfigManager getConfigManager(ItemStack target) {
        ConfigManager out = new ConfigManager((manager, settingName, newValue) -> {
            NBTTagCompound data = Platform.openNbtData(target);
            manager.writeToNBT(data);
        });

        out.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        out.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        out.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);

        out.readFromNBT(Platform.openNbtData(target).copy());

        return out;
    }

    @Override
    public String getEncryptionKey(ItemStack item) {
        NBTTagCompound tag = Platform.openNbtData(item);

        return tag.getString("encryptionKey");
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        NBTTagCompound tag = Platform.openNbtData(item);
        tag.setString("encryptionKey", encKey);
        tag.setString("name", name);
    }

    @Override
    public IGuiHandler getGuiHandler(ItemStack is) {
        // Not used since we open GUI directly, but required by interface
        return null;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged;
    }

    @Optional.Method(modid = "baubles")
    @Override
    public BaubleType getBaubleType(ItemStack itemStack) {
        return BaubleType.TRINKET;
    }

    @Optional.Method(modid = "baubles")
    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        // No magnet logic for disk terminal
    }
}
