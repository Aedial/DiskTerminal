package com.cellterminal.items;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.helpers.IPriorityHost;

import com.cellterminal.Tags;
import com.cellterminal.gui.GuiHandler;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketPriorityApplied;


/**
 * Priority Wand - allows setting priorities on AE2 storage blocks.
 * Shift-right click on a block to open the AE2 priority GUI.
 * Right click on a block to apply the stored priority.
 */
public class ItemPriorityWand extends Item {

    private static final String NBT_PRIORITY = "storedPriority";

    public ItemPriorityWand() {
        this.setRegistryName(Tags.MODID, "priority_wand");
        this.setTranslationKey(Tags.MODID + ".priority_wand");
        this.setMaxStackSize(1);
        this.setCreativeTab(appeng.api.AEApi.instance().definitions().materials().cell1kPart().maybeStack(1)
            .map(s -> s.getItem().getCreativeTab()).orElse(null));
    }

    /**
     * Get the stored priority from the wand.
     */
    public int getStoredPriority(ItemStack stack) {
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null || !nbt.hasKey(NBT_PRIORITY)) return 0;

        return nbt.getInteger(NBT_PRIORITY);
    }

    /**
     * Set the stored priority on the wand.
     */
    public void setStoredPriority(ItemStack stack, int priority) {
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }

        nbt.setInteger(NBT_PRIORITY, priority);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, EnumHand hand) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof IPriorityHost)) return EnumActionResult.PASS;

        // On blocks, shift-click does nothing special (use shift-click in air for GUI)
        // Regular right click: apply stored priority
        if (player.isSneaking()) return EnumActionResult.PASS;

        if (world.isRemote) return EnumActionResult.PASS;

        // Server side: apply stored priority
        IPriorityHost priorityHost = (IPriorityHost) te;
        ItemStack stack = player.getHeldItem(hand);
        int storedPriority = getStoredPriority(stack);
        priorityHost.setPriority(storedPriority);
        te.markDirty();

        // Send highlight effect to client
        CellTerminalNetwork.INSTANCE.sendTo(
            new PacketPriorityApplied(pos, world.provider.getDimension(), storedPriority),
            (EntityPlayerMP) player
        );

        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        // Shift-right click in air: open Priority Wand GUI to set stored priority
        if (player.isSneaking()) {
            if (!world.isRemote) GuiHandler.openPriorityWandGui(player, hand);

            return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
        }

        return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(hand));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        int priority = getStoredPriority(stack);
        tooltip.add(TextFormatting.GOLD + I18n.format("item.cellterminal.priority_wand.stored",
            TextFormatting.WHITE.toString() + priority + TextFormatting.GOLD));
        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + I18n.format("item.cellterminal.priority_wand.tip1"));
        tooltip.add(TextFormatting.AQUA + I18n.format("item.cellterminal.priority_wand.tip2"));
        tooltip.add(TextFormatting.AQUA + I18n.format("item.cellterminal.priority_wand.tip3"));
    }
}
