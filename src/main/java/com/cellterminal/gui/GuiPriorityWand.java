package com.cellterminal.gui;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.widgets.GuiNumberBox;

import com.cellterminal.container.ContainerPriorityWand;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketSetWandPriority;


/**
 * GUI for setting the Priority Wand's stored priority value.
 * Similar to AE2's priority GUI but for the wand itself.
 */
public class GuiPriorityWand extends GuiContainer {

    // Use AE2's priority texture for now (same layout)
    private static final ResourceLocation TEXTURE = new ResourceLocation("appliedenergistics2", "textures/guis/priority.png");

    private GuiNumberBox priorityField;
    private GuiButton plus1, plus10, plus100, plus1000;
    private GuiButton minus1, minus10, minus100, minus1000;

    private final ContainerPriorityWand container;

    public GuiPriorityWand(InventoryPlayer playerInventory, EnumHand hand) {
        super(new ContainerPriorityWand(playerInventory, hand));
        this.container = (ContainerPriorityWand) inventorySlots;
        this.xSize = 176;
        this.ySize = 107;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Priority adjustment buttons
        this.buttonList.add(this.plus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 32, 22, 20, "+1"));
        this.buttonList.add(this.plus10 = new GuiButton(1, this.guiLeft + 48, this.guiTop + 32, 28, 20, "+10"));
        this.buttonList.add(this.plus100 = new GuiButton(2, this.guiLeft + 82, this.guiTop + 32, 32, 20, "+100"));
        this.buttonList.add(this.plus1000 = new GuiButton(3, this.guiLeft + 120, this.guiTop + 32, 38, 20, "+1000"));

        this.buttonList.add(this.minus1 = new GuiButton(4, this.guiLeft + 20, this.guiTop + 69, 22, 20, "-1"));
        this.buttonList.add(this.minus10 = new GuiButton(5, this.guiLeft + 48, this.guiTop + 69, 28, 20, "-10"));
        this.buttonList.add(this.minus100 = new GuiButton(6, this.guiLeft + 82, this.guiTop + 69, 32, 20, "-100"));
        this.buttonList.add(this.minus1000 = new GuiButton(7, this.guiLeft + 120, this.guiTop + 69, 38, 20, "-1000"));

        // Priority text field
        this.priorityField = new GuiNumberBox(this.fontRenderer, this.guiLeft + 62, this.guiTop + 57, 59, this.fontRenderer.FONT_HEIGHT, Long.class);
        this.priorityField.setEnableBackgroundDrawing(false);
        this.priorityField.setMaxStringLength(16);
        this.priorityField.setTextColor(0xFFFFFF);
        this.priorityField.setVisible(true);
        this.priorityField.setFocused(true);
        this.priorityField.setText(String.valueOf(container.getPriority()));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.cellterminal.priority_wand.title");
        this.fontRenderer.drawString(title, 8, 6, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);

        this.priorityField.drawTextBox();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        super.actionPerformed(button);

        int adjustment = 0;

        if (button == this.plus1) adjustment = 1;
        else if (button == this.plus10) adjustment = 10;
        else if (button == this.plus100) adjustment = 100;
        else if (button == this.plus1000) adjustment = 1000;
        else if (button == this.minus1) adjustment = -1;
        else if (button == this.minus10) adjustment = -10;
        else if (button == this.minus100) adjustment = -100;
        else if (button == this.minus1000) adjustment = -1000;

        if (adjustment != 0) {
            addPriority(adjustment);
        }
    }

    private void addPriority(int amount) {
        try {
            String text = this.priorityField.getText();
            if (text.isEmpty()) text = "0";

            long current = Long.parseLong(text);
            current += amount;

            // Clamp to int range
            current = Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, current));

            this.priorityField.setText(String.valueOf(current));
            sendPriorityUpdate((int) current);
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.priorityField.textboxKeyTyped(typedChar, keyCode)) {
            // Send update when typing
            try {
                String text = this.priorityField.getText();
                if (text.isEmpty() || text.equals("-")) return;

                long value = Long.parseLong(text);
                value = Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
                sendPriorityUpdate((int) value);
            } catch (NumberFormatException e) {
                // Ignore invalid input
            }

            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.priorityField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void sendPriorityUpdate(int priority) {
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketSetWandPriority(
            container.getHand() == EnumHand.MAIN_HAND ? 0 : 1,
            priority
        ));
    }
}
