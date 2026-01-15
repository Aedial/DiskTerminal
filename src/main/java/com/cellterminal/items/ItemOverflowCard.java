package com.cellterminal.items;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.core.CreativeTab;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cellterminal.Tags;


/**
 * Overflow Card - an upgrade card for Compacting Storage Cells.
 * When installed, excess items that cannot fit in the cell are voided instead of rejected.
 */
public class ItemOverflowCard extends Item {

    public ItemOverflowCard() {
        setRegistryName(Tags.MODID, "overflow_card");
        setTranslationKey(Tags.MODID + ".overflow_card");
        setMaxStackSize(64);
        setCreativeTab(CreativeTab.instance);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("\u00a77" + I18n.format("tooltip.cellterminal.overflow_card.desc"));
        tooltip.add("\u00a78" + I18n.format("tooltip.cellterminal.overflow_card.use"));
    }
}
