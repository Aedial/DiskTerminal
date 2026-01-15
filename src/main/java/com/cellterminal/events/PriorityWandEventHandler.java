package com.cellterminal.events;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import appeng.helpers.IPriorityHost;

import com.cellterminal.items.ItemPriorityWand;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketPriorityApplied;


/**
 * Event handler for Priority Wand auto-apply feature.
 * When a player places a block that supports priority (IPriorityHost) while
 * holding a Priority Wand in their off-hand, automatically apply the stored priority.
 */
public class PriorityWandEventHandler {

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        EntityPlayer player = event.getPlayer();
        if (player == null || player.world.isRemote) return;

        // Check if player has Priority Wand in off-hand
        ItemStack offHandStack = player.getHeldItem(EnumHand.OFF_HAND);
        if (offHandStack.isEmpty() || !(offHandStack.getItem() instanceof ItemPriorityWand)) return;

        // Schedule a delayed check for the TileEntity (it may not exist immediately after placement)
        World world = event.getWorld();
        BlockPos pos = event.getPos();

        // Use server scheduled task to check on next tick when TileEntity should exist
        if (player instanceof EntityPlayerMP) {
            EntityPlayerMP serverPlayer = (EntityPlayerMP) player;

            serverPlayer.getServerWorld().addScheduledTask(() -> {
                TileEntity te = world.getTileEntity(pos);
                if (!(te instanceof IPriorityHost)) return;

                ItemPriorityWand wand = (ItemPriorityWand) offHandStack.getItem();
                int storedPriority = wand.getStoredPriority(offHandStack);

                IPriorityHost priorityHost = (IPriorityHost) te;
                priorityHost.setPriority(storedPriority);
                te.markDirty();

                // Send visual feedback
                CellTerminalNetwork.INSTANCE.sendTo(
                    new PacketPriorityApplied(pos, world.provider.getDimension(), storedPriority),
                    serverPlayer
                );
            });
        }
    }
}
