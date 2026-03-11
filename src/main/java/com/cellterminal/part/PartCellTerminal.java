package com.cellterminal.part;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

import net.minecraftforge.items.IItemHandler;

import appeng.api.parts.IPartModel;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractPartDisplay;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;

import com.cellterminal.Tags;
import com.cellterminal.gui.GuiHandler;


/**
 * Part representing the Cell Terminal.
 * Can store temporary cells for partitioning before sending them to the network.
 */
public class PartCellTerminal extends AbstractPartDisplay implements IAEAppEngInventory {

    private static final ResourceLocation MODEL_BASE = new ResourceLocation("appliedenergistics2", "part/display_base");
    private static final ResourceLocation MODEL_ON = new ResourceLocation(Tags.MODID, "part/cell_terminal_on");
    private static final ResourceLocation MODEL_ON_DIM = new ResourceLocation(Tags.MODID, "part/cell_terminal_on_dim");
    private static final ResourceLocation MODEL_OFF = new ResourceLocation(Tags.MODID, "part/cell_terminal_off");
    private static final ResourceLocation MODEL_STATUS_OFF = new ResourceLocation("appliedenergistics2", "part/display_status_off");
    private static final ResourceLocation MODEL_STATUS_ON = new ResourceLocation("appliedenergistics2", "part/display_status_on");
    private static final ResourceLocation MODEL_STATUS_HAS_CHANNEL = new ResourceLocation("appliedenergistics2", "part/display_status_has_channel");

    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON_DIM, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    // Maximum slots for temporary cell storage (can hold up to 16 cells)
    private static final int MAX_TEMP_CELLS = 16;

    // Inventory for temporary cell storage (cells can be placed here for partitioning)
    private final AppEngInternalInventory tempCellInventory = new AppEngInternalInventory(this, MAX_TEMP_CELLS, 1);

    public PartCellTerminal(ItemStack is) {
        super(is);
    }

    public static ResourceLocation[] getResources() {
        return new ResourceLocation[] {
            MODEL_BASE,
            MODEL_ON,
            MODEL_ON_DIM,
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
                GuiHandler.openCellTerminalGui(player, this.getHost().getTile(), this.getSide());
            }
        }

        return true;
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    // ========================================
    // TEMP CELL STORAGE
    // ========================================

    /**
     * Get the temp cell inventory for GUI access.
     * Only allows ICellWorkbenchItem items (storage cells).
     */
    public AppEngInternalInventory getTempCellInventory() {
        return this.tempCellInventory;
    }

    /**
     * Get the number of currently stored temp cells.
     */
    public int getTempCellCount() {
        int count = 0;
        for (int i = 0; i < tempCellInventory.getSlots(); i++) {
            if (!tempCellInventory.getStackInSlot(i).isEmpty()) count++;
        }

        return count;
    }

    /**
     * Get the first empty slot index, or -1 if full.
     */
    public int getFirstEmptyTempSlot() {
        for (int i = 0; i < tempCellInventory.getSlots(); i++) {
            if (tempCellInventory.getStackInSlot(i).isEmpty()) return i;
        }

        return -1;
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        if ("tempCells".equals(name)) return this.tempCellInventory;

        return super.getInventoryByName(name);
    }

    // ========================================
    // NBT PERSISTENCE
    // ========================================

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.tempCellInventory.readFromNBT(data, "tempCells");
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        this.tempCellInventory.writeToNBT(data, "tempCells");
    }

    // ========================================
    // DROP ON REMOVE
    // ========================================

    @Override
    public void getDrops(List<ItemStack> drops, boolean wrenched) {
        super.getDrops(drops, wrenched);

        // Drop all temp cells when the part is removed
        for (int i = 0; i < tempCellInventory.getSlots(); i++) {
            ItemStack cell = tempCellInventory.getStackInSlot(i);
            if (!cell.isEmpty()) drops.add(cell.copy());
        }
    }

    // ========================================
    // INVENTORY CHANGE CALLBACK
    // ========================================

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removedStack, ItemStack addedStack) {
        // Mark the host tile as needing a save when inventory changes
        if (this.getHost() != null && this.getHost().getTile() != null) this.getHost().markForSave();
    }

    @Override
    public void saveChanges() {
        if (this.getHost() != null) this.getHost().markForSave();
    }
}
