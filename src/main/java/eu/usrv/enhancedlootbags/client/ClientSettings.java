package eu.usrv.enhancedlootbags.client;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import cpw.mods.fml.common.Loader;
import eu.usrv.enhancedlootbags.EnhancedLootBags;

public class ClientSettings {

    private static final String CATEGORY_GUI = "gui";
    private static final String KEY_LIGHT_THEME = "StatisticsLightTheme";

    private static Configuration config = null;

    private static Property getLightThemeProperty() {
        if (config == null) {
            File dir = new File(Loader.instance().getConfigDir(), EnhancedLootBags.NICEFOLDERNAME);
            if (!dir.exists()) dir.mkdirs();
            config = new Configuration(new File(dir, EnhancedLootBags.NICEFOLDERNAME + "_client.cfg"));
            config.load();
        }
        Property property = config.get(
                CATEGORY_GUI,
                KEY_LIGHT_THEME,
                false,
                "Use the light theme for the LootBag statistics screen. Can also be toggled inside the screen");
        if (config.hasChanged()) config.save();
        return property;
    }

    public static boolean isLightTheme() {
        return getLightThemeProperty().getBoolean(false);
    }

    public static void setLightTheme(boolean lightTheme) {
        getLightThemeProperty().set(lightTheme);
        config.save();
    }
}
