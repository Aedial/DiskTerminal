package com.diskterminal.part;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

import appeng.api.parts.IPartModel;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractPartDisplay;
import appeng.util.Platform;

import com.diskterminal.Tags;
import com.diskterminal.gui.GuiHandler;


public class PartDiskTerminal extends AbstractPartDisplay {

    private static final ResourceLocation MODEL_BASE = new ResourceLocation("appliedenergistics2", "part/display_base");
    private static final ResourceLocation MODEL_ON = new ResourceLocation(Tags.MODID, "part/disk_terminal_on");
    private static final ResourceLocation MODEL_OFF = new ResourceLocation(Tags.MODID, "part/disk_terminal_off");
    private static final ResourceLocation MODEL_STATUS_OFF = new ResourceLocation("appliedenergistics2", "part/display_status_off");
    private static final ResourceLocation MODEL_STATUS_ON = new ResourceLocation("appliedenergistics2", "part/display_status_on");
    private static final ResourceLocation MODEL_STATUS_HAS_CHANNEL = new ResourceLocation("appliedenergistics2", "part/display_status_has_channel");

    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    public PartDiskTerminal(ItemStack is) {
        super(is);
    }

    public static ResourceLocation[] getResources() {
        return new ResourceLocation[] {
            MODEL_BASE,
            MODEL_ON,
            MODEL_OFF,
            MODEL_STATUS_OFF,
            MODEL_STATUS_ON,
            MODEL_STATUS_HAS_CHANNEL
        };
    }

    @Override
    public boolean onPartActivate(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (!super.onPartActivate(player, hand, pos)) {
            if (Platform.isServer()) {
                GuiHandler.openDiskTerminalGui(player, this.getHost().getTile(), this.getSide());
            }
        }

        return true;
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }
}
