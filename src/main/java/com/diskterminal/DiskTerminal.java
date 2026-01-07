package com.diskterminal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.diskterminal.gui.GuiHandler;
import com.diskterminal.network.DiskTerminalNetwork;
import com.diskterminal.proxy.CommonProxy;


@Mod(
    modid = Tags.MODID,
    name = Tags.MODNAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "required-after:appliedenergistics2;"
)
public class DiskTerminal {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MODID);

    @Mod.Instance(Tags.MODID)
    public static DiskTerminal instance;

    @SidedProxy(
        clientSide = "com.diskterminal.proxy.ClientProxy",
        serverSide = "com.diskterminal.proxy.ServerProxy"
    )
    public static CommonProxy proxy;

    public GuiHandler guiHandler;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        DiskTerminalNetwork.init();
        proxy.preInit(event);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
