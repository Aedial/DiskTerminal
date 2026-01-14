package com.cellterminal.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import org.lwjgl.input.Keyboard;


/**
 * Keybindings for Cell Terminal.
 * All default to NONE (unbound) to avoid conflicts.
 */
public enum KeyBindings {

    /**
     * Quick partition with automatic type inference.
     * WARNING: May misattribute types (e.g., buckets become item partitions, not fluid).
     */
    QUICK_PARTITION_AUTO(new KeyBinding(
        "key.cellterminal.quick_partition_auto.desc",
        KeyConflictContext.GUI,
        Keyboard.KEY_NONE,
        "key.cellterminal.category"
    )),

    /**
     * Quick partition into an item cell.
     */
    QUICK_PARTITION_ITEM(new KeyBinding(
        "key.cellterminal.quick_partition_item.desc",
        KeyConflictContext.GUI,
        Keyboard.KEY_NONE,
        "key.cellterminal.category"
    )),

    /**
     * Quick partition into a fluid cell.
     */
    QUICK_PARTITION_FLUID(new KeyBinding(
        "key.cellterminal.quick_partition_fluid.desc",
        KeyConflictContext.GUI,
        Keyboard.KEY_NONE,
        "key.cellterminal.category"
    )),

    /**
     * Quick partition into an essentia cell.
     * Note: Useless if Thaumic Energistics mod is not loaded.
     */
    QUICK_PARTITION_ESSENTIA(new KeyBinding(
        "key.cellterminal.quick_partition_essentia.desc",
        KeyConflictContext.GUI,
        Keyboard.KEY_NONE,
        "key.cellterminal.category"
    ));

    private final KeyBinding keyBinding;

    KeyBindings(KeyBinding keyBinding) {
        this.keyBinding = keyBinding;
    }

    public KeyBinding getKeyBinding() {
        return keyBinding;
    }

    /**
     * Check if this keybinding is pressed (matches the given keycode).
     */
    public boolean isActiveAndMatches(int keyCode) {
        return keyBinding.isActiveAndMatches(keyCode);
    }

    /**
     * Check if this keybinding is bound (not NONE).
     */
    public boolean isBound() {
        return keyBinding.getKeyCode() != Keyboard.KEY_NONE;
    }

    /**
     * Get the display name for this keybinding.
     */
    public String getDisplayName() {
        return keyBinding.getDisplayName();
    }

    /**
     * Register all keybindings with Forge.
     */
    public static void registerAll() {
        for (KeyBindings kb : values()) ClientRegistry.registerKeyBinding(kb.getKeyBinding());
    }
}
