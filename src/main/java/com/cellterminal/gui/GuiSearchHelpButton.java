package com.cellterminal.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;


/**
 * A small "?" button that shows search syntax help when hovered.
 */
public class GuiSearchHelpButton extends GuiButton {

    public static final int BUTTON_SIZE = 10;

    public GuiSearchHelpButton(int buttonId, int x, int y) {
        super(buttonId, x, y, BUTTON_SIZE, BUTTON_SIZE, "?");
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        // Draw button background
        int bgColor = this.hovered ? 0xFF505050 : 0xFF606060;
        drawRect(this.x, this.y, this.x + this.width, this.y + this.height, bgColor);

        // Draw border
        drawRect(this.x, this.y, this.x + this.width, this.y + 1, 0xFF808080);
        drawRect(this.x, this.y, this.x + 1, this.y + this.height, 0xFF808080);
        drawRect(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, 0xFF303030);
        drawRect(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, 0xFF303030);

        // Draw "?" centered
        int textX = this.x + (this.width - mc.fontRenderer.getStringWidth("?")) / 2;
        int textY = this.y + (this.height - 8) / 2;
        int textColor = this.hovered ? 0xFFFFFF00 : 0xFFCCCCCC;
        mc.fontRenderer.drawString("?", textX, textY, textColor);
    }

    /**
     * Get the tooltip lines explaining search syntax.
     */
    public List<String> getTooltip() {
        List<String> tooltip = new ArrayList<>();
        tooltip.add("§e" + I18n.format("gui.cellterminal.search_help.title"));
        tooltip.add("");
        tooltip.add(I18n.format("gui.cellterminal.search_help.simple"));
        tooltip.add("§7" + I18n.format("gui.cellterminal.search_help.simple_desc"));
        tooltip.add("");
        tooltip.add(I18n.format("gui.cellterminal.search_help.advanced"));
        tooltip.add("§7" + I18n.format("gui.cellterminal.search_help.advanced_desc"));
        tooltip.add("");
        tooltip.add("§b" + I18n.format("gui.cellterminal.search_help.identifiers"));
        tooltip.add("§7  $name §f- " + I18n.format("gui.cellterminal.search_help.id_name"));
        tooltip.add("§7  $priority §f- " + I18n.format("gui.cellterminal.search_help.id_priority"));
        tooltip.add("§7  $partition §f- " + I18n.format("gui.cellterminal.search_help.id_partition"));
        tooltip.add("§7  $items §f- " + I18n.format("gui.cellterminal.search_help.id_items"));
        tooltip.add("");
        tooltip.add("§b" + I18n.format("gui.cellterminal.search_help.operators"));
        tooltip.add("§7  = != < > <= >= ~ §f- " + I18n.format("gui.cellterminal.search_help.op_compare"));
        tooltip.add("§7  & | §f- " + I18n.format("gui.cellterminal.search_help.op_logic"));
        tooltip.add("§7  ( ) §f- " + I18n.format("gui.cellterminal.search_help.op_group"));
        tooltip.add("");
        tooltip.add("§b" + I18n.format("gui.cellterminal.search_help.examples"));
        tooltip.add("§7  ? $priority>0");
        tooltip.add("§7  ? $items=0 & $partition>0");
        tooltip.add("§7  ? $name~iron | $name~gold | $name=diamond");

        // TODO: add "double-click to expand search box" line

        return tooltip;
    }
}
