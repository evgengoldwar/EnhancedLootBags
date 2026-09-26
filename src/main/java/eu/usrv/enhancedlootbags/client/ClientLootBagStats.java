package eu.usrv.enhancedlootbags.client;

import eu.usrv.enhancedlootbags.core.stats.LootBagStats;
import eu.usrv.enhancedlootbags.core.stats.LootBagStats.GroupStats;

/**
 * CLIENTSIDE Holds the lootbag statistics the server has sent for the local player. The stats object is replaced as a
 * whole on every update, so the GUI can safely keep reading a snapshot while packets arrive
 */
public class ClientLootBagStats {

    private static volatile LootBagStats sStats = new LootBagStats();

    public static LootBagStats getStats() {
        return sStats;
    }

    public static synchronized void setStats(LootBagStats pStats) {
        sStats = pStats;
    }

    public static synchronized void updateGroup(GroupStats pGroup) {
        LootBagStats tCopy = new LootBagStats();
        for (GroupStats tGrp : sStats.getGroups()) tCopy.putGroup(tGrp);
        tCopy.putGroup(pGroup);
        sStats = tCopy;
    }

    public static synchronized void clear() {
        sStats = new LootBagStats();
    }
}
