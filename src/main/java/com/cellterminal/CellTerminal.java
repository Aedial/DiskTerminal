package com.cellterminal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.gui.GuiHandler;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.proxy.CommonProxy;


@Mod(
    modid = Tags.MODID,
    name = Tags.MODNAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "required-after:appliedenergistics2;",
    guiFactory = "com.cellterminal.config.CellTerminalConfigGuiFactory"
)
public class CellTerminal {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MODID);

    @Mod.Instance(Tags.MODID)
    public static CellTerminal instance;

    @SidedProxy(
        clientSide = "com.cellterminal.proxy.ClientProxy",
        serverSide = "com.cellterminal.proxy.ServerProxy"
    )
    public static CommonProxy proxy;

    public GuiHandler guiHandler;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        CellTerminalNetwork.init();
        proxy.preInit(event);

        // Register this instance for config change events
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!event.getModID().equals(Tags.MODID)) return;

        CellTerminalServerConfig.getInstance().syncFromConfig();
    }
}
