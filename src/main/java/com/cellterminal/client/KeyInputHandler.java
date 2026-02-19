package com.cellterminal.client;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;

import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketOpenWirelessTerminal;


/**
 * Handles key input events for Cell Terminal keybinds that work outside of GUIs.
 * Registered on the Forge event bus in ClientProxy.
 */
public class KeyInputHandler {

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!KeyBindings.OPEN_WIRELESS_TERMINAL.getKeyBinding().isPressed()) return;

        CellTerminalNetwork.INSTANCE.sendToServer(new PacketOpenWirelessTerminal());
    }
}
