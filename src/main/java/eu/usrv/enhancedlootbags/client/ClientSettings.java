package eu.usrv.enhancedlootbags.client;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import cpw.mods.fml.common.Loader;
import eu.usrv.enhancedlootbags.EnhancedLootBags;

/**
 * CLIENTSIDE Personal settings of the player that survive restarts, like the theme of the statistics GUI. Stored in
 * config/EnhancedLootBags/EnhancedLootBags_client.cfg
 */
public class ClientSettings {

    private static final String CATEGORY_GUI = "gui";
    private static final String KEY_LIGHT_THEME = "StatisticsLightTheme";

    private static Configuration sConfig = null;

    private static Configuration getConfig() {
        if (sConfig == null) {
            File tDir = new File(Loader.instance().getConfigDir(), EnhancedLootBags.NICEFOLDERNAME);
            if (!tDir.exists()) tDir.mkdirs();
            sConfig = new Configuration(new File(tDir, EnhancedLootBags.NICEFOLDERNAME + "_client.cfg"));
            sConfig.load();
            // Create the entry with its comment right away, so it can be found in the file
            getLightThemeProperty();
            if (sConfig.hasChanged()) sConfig.save();
        }
        return sConfig;
    }

    private static Property getLightThemeProperty() {
        return sConfig.get(
                CATEGORY_GUI,
                KEY_LIGHT_THEME,
                false,
                "Use the light theme for the LootBag statistics screen. Can also be toggled inside the screen");
    }

    public static boolean isLightTheme() {
        getConfig();
        return getLightThemeProperty().getBoolean(false);
    }

    public static void setLightTheme(boolean pLightTheme) {
        getConfig();
        getLightThemeProperty().set(pLightTheme);
        sConfig.save();
    }
}
