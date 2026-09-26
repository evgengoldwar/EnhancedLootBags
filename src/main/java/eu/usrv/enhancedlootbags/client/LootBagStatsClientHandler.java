package eu.usrv.enhancedlootbags.client;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import eu.usrv.enhancedlootbags.client.gui.GuiLootBagStats;

/**
 * CLIENTSIDE Opens the lootbag statistics GUI via keybinding or the /lootbagstats client command
 */
public class LootBagStatsClientHandler {

    public static final KeyBinding KEY_OPEN_STATS = new KeyBinding(
            "enhancedlootbags.key.open_stats",
            Keyboard.KEY_NONE,
            "enhancedlootbags.key.category");

    private static int sOpenRequestTicks = 0;

    /**
     * Open the statistics GUI as soon as no other screen is shown (the chat screen closes itself only after a command
     * has been executed). The request expires after one second
     */
    public static void requestOpen() {
        sOpenRequestTicks = 20;
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent pEvent) {
        if (KEY_OPEN_STATS.isPressed() && Minecraft.getMinecraft().currentScreen == null) requestOpen();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent pEvent) {
        if (pEvent.phase != TickEvent.Phase.END || sOpenRequestTicks <= 0) return;

        sOpenRequestTicks--;
        Minecraft tMC = Minecraft.getMinecraft();
        if (tMC.thePlayer == null || tMC.currentScreen != null) return;

        sOpenRequestTicks = 0;
        tMC.displayGuiScreen(new GuiLootBagStats());
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent pEvent) {
        ClientLootBagStats.clear();
    }

    public static class StatsCommand extends CommandBase {

        @Override
        public String getCommandName() {
            return "lootbagstats";
        }

        @Override
        public List getCommandAliases() {
            return Arrays.asList("lbstats");
        }

        @Override
        public String getCommandUsage(ICommandSender pSender) {
            return "/lootbagstats";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender pSender) {
            return true;
        }

        @Override
        public void processCommand(ICommandSender pSender, String[] pArgs) {
            requestOpen();
        }
    }
}
