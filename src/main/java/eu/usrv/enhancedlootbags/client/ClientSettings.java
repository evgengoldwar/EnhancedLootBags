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

    private static Configuration getConfig() {
        if (config == null) {
            File dir = new File(Loader.instance().getConfigDir(), EnhancedLootBags.NICEFOLDERNAME);
            if (!dir.exists()) dir.mkdirs();
            config = new Configuration(new File(dir, EnhancedLootBags.NICEFOLDERNAME + "_client.cfg"));
            config.load();

            getLightThemeProperty();
            if (config.hasChanged()) config.save();
        }
        return config;
    }

    private static Property getLightThemeProperty() {
        return config.get(
                CATEGORY_GUI,
                KEY_LIGHT_THEME,
                false,
                "Use the light theme for the LootBag statistics screen. Can also be toggled inside the screen");
    }

    public static boolean isLightTheme() {
        getConfig();
        return getLightThemeProperty().getBoolean(false);
    }

    public static void setLightTheme(boolean lightTheme) {
        getConfig();
        getLightThemeProperty().set(lightTheme);
        config.save();
    }
}
