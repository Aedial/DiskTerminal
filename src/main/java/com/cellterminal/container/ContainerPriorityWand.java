package com.cellterminal.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import com.cellterminal.items.ItemPriorityWand;


/**
 * Container for the Priority Wand GUI.
 * Allows the player to set the stored priority value.
 */
public class ContainerPriorityWand extends Container {

    private final EntityPlayer player;
    private final EnumHand hand;
    private final ItemStack wandStack;

    public ContainerPriorityWand(InventoryPlayer playerInventory, EnumHand hand) {
        this.player = playerInventory.player;
        this.hand = hand;
        this.wandStack = player.getHeldItem(hand);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        ItemStack currentStack = player.getHeldItem(hand);

        return !currentStack.isEmpty() && currentStack.getItem() instanceof ItemPriorityWand;
    }

    /**
     * Get the priority stored in the wand.
     */
    public int getPriority() {
        if (wandStack.getItem() instanceof ItemPriorityWand) {
            return ((ItemPriorityWand) wandStack.getItem()).getStoredPriority(wandStack);
        }

        return 0;
    }

    /**
     * Set the priority stored in the wand.
     */
    public void setPriority(int priority) {
        if (wandStack.getItem() instanceof ItemPriorityWand) {
            ((ItemPriorityWand) wandStack.getItem()).setStoredPriority(wandStack, priority);
        }
    }

    public EnumHand getHand() {
        return hand;
    }
}
