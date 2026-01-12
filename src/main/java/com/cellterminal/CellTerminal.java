package com.cellterminal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.cellterminal.gui.GuiHandler;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.proxy.CommonProxy;


@Mod(
    modid = Tags.MODID,
    name = Tags.MODNAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "required-after:appliedenergistics2;"
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
