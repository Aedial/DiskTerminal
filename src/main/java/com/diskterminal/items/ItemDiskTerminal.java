package com.diskterminal.items;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.core.CreativeTab;

import com.diskterminal.Tags;
import com.diskterminal.part.PartDiskTerminal;


public class ItemDiskTerminal extends Item implements IPartItem {

    public ItemDiskTerminal() {
        this.setRegistryName(Tags.MODID, "disk_terminal");
        this.setTranslationKey(Tags.MODID + ".disk_terminal");
        this.setCreativeTab(CreativeTab.instance);
        this.setMaxStackSize(64);
    }

    @Nullable
    @Override
    public IPart createPartFromItemStack(ItemStack stack) {
        return new PartDiskTerminal(stack);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                      EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        return AEApi.instance().partHelper().placeBus(player.getHeldItem(hand), pos, facing, player, hand, world);
    }
}
