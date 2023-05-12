package tterrag.wailaplugins;

import static tterrag.wailaplugins.WailaPlugins.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.enderio.core.IEnderMod;
import com.enderio.core.common.Lang;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import tterrag.wailaplugins.config.WPConfigHandler;
import tterrag.wailaplugins.plugins.PluginRegistrar;
import tterrag.wailaplugins.proxy.CommonProxy;

@Mod(
        modid = MODID,
        name = NAME,
        version = VERSION,
        dependencies = DEPENDENCIES,
        guiFactory = "tterrag.wailaplugins.client.config.WPConfigFactory")
public class WailaPlugins implements IEnderMod {

    public static final String MODID = "wailaplugins";
    public static final String NAME = "WAILA Plugins";
    public static final String VERSION = "GRADLETOKEN_VERSION";
    public static final String DEPENDENCIES = "after:endercore;after:Waila;";

    @SidedProxy(
            serverSide = "tterrag.wailaplugins.proxy.CommonProxy",
            clientSide = "tterrag.wailaplugins.proxy.ClientProxy")
    public static CommonProxy proxy;

    public static final Logger logger = LogManager.getLogger(NAME);

    public static final Lang lang = new Lang("wp.hud.msg");

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        WPConfigHandler.INSTANCE.initialize(event.getSuggestedConfigurationFile());

        PluginRegistrar.INSTANCE.preInit(event);
    }

    @EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        PluginRegistrar.INSTANCE.postInit();
    }

    @Override
    public String modid() {
        return MODID;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String version() {
        return VERSION;
    }
}
