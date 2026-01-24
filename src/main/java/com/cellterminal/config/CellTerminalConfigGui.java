package com.cellterminal.config;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import com.cellterminal.Tags;


/**
 * In-game configuration GUI for Cell Terminal.
 * Allows editing server config settings and client settings through Forge's config GUI system.
 * Note: Client config persistent state (filters, tabs) is intentionally hidden.
 */
public class CellTerminalConfigGui extends GuiConfig {

    public CellTerminalConfigGui(GuiScreen parent) {
        super(
            parent,
            getConfigElements(),
            Tags.MODID,
            false,
            false,
            GuiConfig.getAbridgedConfigPath(CellTerminalServerConfig.getInstance().getConfigFilePath())
        );
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();

        CellTerminalServerConfig.getInstance().syncFromConfig();
        CellTerminalClientConfig.getInstance().syncFromConfig();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // ESC key should save config like Done button
        if (keyCode == Keyboard.KEY_ESCAPE && this.entryList.hasChangedEntry(true)) {
            this.entryList.saveConfigElements();
            CellTerminalServerConfig.getInstance().syncFromConfig();
            CellTerminalClientConfig.getInstance().syncFromConfig();
        }

        super.keyTyped(typedChar, keyCode);
    }

    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> elements = new ArrayList<>();

        // Add client config settings category (user-configurable settings only, not persistent state)
        ConfigCategory clientSettings = CellTerminalClientConfig.getInstance().getConfiguration()
            .getCategory(CellTerminalClientConfig.getInstance().getSettingsCategoryName());
        elements.add(new ConfigElement(clientSettings));

        // Add server config categories
        for (String categoryName : CellTerminalServerConfig.getInstance().getCategoryNames()) {
            ConfigCategory category = CellTerminalServerConfig.getInstance().getCategory(categoryName);

            if (category.isChild()) continue;

            elements.add(new ConfigElement(category));
        }

        return elements;
    }
}
