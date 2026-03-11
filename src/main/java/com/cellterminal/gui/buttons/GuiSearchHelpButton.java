package com.cellterminal.gui.buttons;

import java.util.ArrayList;
import java.util.List;

import com.cellterminal.gui.GuiConstants;
import net.minecraft.client.resources.I18n;


/**
 * A small "?" button that shows search syntax help when hovered.
 */
public class GuiSearchHelpButton extends GuiAtlasButton {

    public static final int SIZE = GuiConstants.TOOLTIP_BUTTON_SIZE;

    public GuiSearchHelpButton(int buttonId, int x, int y) {
        super(buttonId, x, y, SIZE);
    }

    @Override
    protected int getBackgroundTexX() {
        return GuiConstants.TOOLTIP_BUTTON_X;
    }

    @Override
    protected int getBackgroundTexY() {
        return GuiConstants.TOOLTIP_BUTTON_Y + (this.hovered ? SIZE : 0);
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
        tooltip.add("§7  $content §f- " + I18n.format("gui.cellterminal.search_help.id_content"));
        tooltip.add("§7  $part §f- " + I18n.format("gui.cellterminal.search_help.id_part"));
        tooltip.add("§7  $container §f- " + I18n.format("gui.cellterminal.search_help.id_container"));
        tooltip.add("§7  $renamed §f- " + I18n.format("gui.cellterminal.search_help.id_renamed"));
        tooltip.add("§7  $priority §f- " + I18n.format("gui.cellterminal.search_help.id_priority"));
        tooltip.add("§7  $partition §f- " + I18n.format("gui.cellterminal.search_help.id_partition"));
        tooltip.add("§7  $items §f- " + I18n.format("gui.cellterminal.search_help.id_items"));
        tooltip.add("");
        tooltip.add("§b" + I18n.format("gui.cellterminal.search_help.operators"));
        tooltip.add("§7  = != < > <= >= ~ §f- " + I18n.format("gui.cellterminal.search_help.op_compare"));
        tooltip.add("§7  & | §f- " + I18n.format("gui.cellterminal.search_help.op_logic"));
        tooltip.add("§7  ( ) §f- " + I18n.format("gui.cellterminal.search_help.op_group"));
        tooltip.add("§7  * ? §f- " + I18n.format("gui.cellterminal.search_help.op_wildcard"));
        tooltip.add("§7  , §f- " + I18n.format("gui.cellterminal.search_help.op_multi"));
        tooltip.add("");
        tooltip.add("§b" + I18n.format("gui.cellterminal.search_help.examples"));
        tooltip.add("§f  ? $priority>0§7 => " + I18n.format("gui.cellterminal.search_help.examples_desc1"));
        tooltip.add("§f  ? $items=0 & $partition>0§7 => " + I18n.format("gui.cellterminal.search_help.examples_desc2"));
        tooltip.add("§f  ? $name~iron,gold,diamond§7 => " + I18n.format("gui.cellterminal.search_help.examples_desc3"));
        tooltip.add("§f  ? $name=*ore & $priority<0§7 => " + I18n.format("gui.cellterminal.search_help.examples_desc4"));
        tooltip.add("§f  ? $container~drive§7 => " + I18n.format("gui.cellterminal.search_help.examples_desc5"));
        tooltip.add("§f  ? $renamed~*§7 => " + I18n.format("gui.cellterminal.search_help.examples_desc6"));
        tooltip.add("§f  ? $content~iron & $part~gold§7 => " + I18n.format("gui.cellterminal.search_help.examples_desc7"));
        tooltip.add("");
        tooltip.add("§7" + I18n.format("gui.cellterminal.search_help.field_double_click"));

        return tooltip;
    }
}
