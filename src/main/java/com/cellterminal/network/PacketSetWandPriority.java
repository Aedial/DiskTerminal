package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.items.ItemPriorityWand;


/**
 * Packet sent from client to server to set the Priority Wand's stored priority.
 */
public class PacketSetWandPriority implements IMessage {

    private int hand;  // 0 = main hand, 1 = off hand
    private int priority;

    public PacketSetWandPriority() {
    }

    public PacketSetWandPriority(int hand, int priority) {
        this.hand = hand;
        this.priority = priority;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.hand = buf.readInt();
        this.priority = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(hand);
        buf.writeInt(priority);
    }

    public static class Handler implements IMessageHandler<PacketSetWandPriority, IMessage> {

        @Override
        public IMessage onMessage(PacketSetWandPriority message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                EnumHand enumHand = message.hand == 0 ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND;
                ItemStack stack = player.getHeldItem(enumHand);

                if (stack.getItem() instanceof ItemPriorityWand) {
                    ((ItemPriorityWand) stack.getItem()).setStoredPriority(stack, message.priority);
                }
            });

            return null;
        }
    }
}
