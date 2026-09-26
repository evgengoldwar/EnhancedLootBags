package eu.usrv.enhancedlootbags.core.stats;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import eu.usrv.enhancedlootbags.EnhancedLootBags;
import eu.usrv.enhancedlootbags.core.serializer.LootGroups.LootGroup.Drop;
import eu.usrv.enhancedlootbags.core.stats.LootBagStats.GroupStats;
import eu.usrv.enhancedlootbags.net.msg.LootBagStatsSyncMessage;
import eu.usrv.yamcore.auxiliary.LogHelper;

public class LootBagStatsHandler {

    private static final String FILE_NAME = "LootBagStats.dat";

    private final LogHelper _mLogger = EnhancedLootBags.Logger;
    private final Map<UUID, LootBagStats> _mPlayerStats = new HashMap<>();
    private File _mSaveDir = null;
    private boolean _mDirty = false;

    private void initStorage() {
        File tSaveDir = DimensionManager.getCurrentSaveRootDirectory();
        if (tSaveDir == null || tSaveDir.equals(_mSaveDir)) return;

        _mPlayerStats.clear();
        _mDirty = false;
        _mSaveDir = tSaveDir;

        File tFile = new File(tSaveDir, FILE_NAME);
        if (!tFile.exists()) return;

        try (FileInputStream tIn = new FileInputStream(tFile)) {
            NBTTagCompound tRoot = CompressedStreamTools.readCompressed(tIn);
            for (Object tKey : tRoot.func_150296_c()) {
                String tUUID = (String) tKey;
                try {
                    LootBagStats tStats = LootBagStats.readFromNBT(tRoot.getCompoundTag(tUUID));
                    updateTrashFlags(tStats);
                    _mPlayerStats.put(UUID.fromString(tUUID), tStats);
                } catch (IllegalArgumentException e) {
                    _mLogger.warn(String.format("[LootBags] Skipping invalid player entry %s in %s", tUUID, FILE_NAME));
                }
            }
        } catch (Exception e) {
            _mLogger.error(String.format("[LootBags] Unable to load %s; Statistics will start fresh", FILE_NAME));
            e.printStackTrace();
        }
    }

    private void updateTrashFlags(LootBagStats pStats) {
        for (GroupStats tGroup : pStats.getGroups()) {
            if (EnhancedLootBags.LootGroupHandler.getGroupByID(tGroup.getGroupID()) == null) continue;
            for (LootBagStats.DropStats tDrop : tGroup.getDrops())
                tDrop.setTrash(EnhancedLootBags.LootGroupHandler.isTrashDrop(tGroup.getGroupID(), tDrop.getDropID()));
        }
    }

    public void save() {
        if (!_mDirty || _mSaveDir == null) return;

        NBTTagCompound tRoot = new NBTTagCompound();
        for (Map.Entry<UUID, LootBagStats> tEntry : _mPlayerStats.entrySet())
            tRoot.setTag(tEntry.getKey().toString(), tEntry.getValue().writeToNBT());

        File tFile = new File(_mSaveDir, FILE_NAME);
        File tTmpFile = new File(_mSaveDir, FILE_NAME + ".tmp");
        try {
            try (FileOutputStream tOut = new FileOutputStream(tTmpFile)) {
                CompressedStreamTools.writeCompressed(tRoot, tOut);
            }
            if (tFile.exists() && !tFile.delete()) throw new IllegalStateException("Unable to replace " + tFile);
            if (!tTmpFile.renameTo(tFile)) throw new IllegalStateException("Unable to rename " + tTmpFile);
            _mDirty = false;
        } catch (Exception e) {
            _mLogger.error(String.format("[LootBags] Unable to save %s", FILE_NAME));
            e.printStackTrace();
        }
    }

    public void unload() {
        save();
        _mPlayerStats.clear();
        _mSaveDir = null;
    }

    public LootBagStats getStats(EntityPlayer pPlayer) {
        initStorage();
        UUID tUUID = pPlayer.getUniqueID();
        LootBagStats tStats = _mPlayerStats.get(tUUID);
        if (tStats == null) {
            tStats = new LootBagStats();
            _mPlayerStats.put(tUUID, tStats);
        }
        return tStats;
    }

    public void recordOpening(EntityPlayer pPlayer, int pGroupID, OpenRecord pRecord) {
        GroupStats tGroup = getStats(pPlayer).getOrCreateGroup(pGroupID);
        tGroup.incrementOpened();
        for (int i = 0; i < pRecord.mDrops.size(); i++) {
            String tDropID = pRecord.mDrops.get(i).getIdentifier();
            tGroup.recordDrop(
                    tDropID,
                    pRecord.mStacks.get(i),
                    EnhancedLootBags.LootGroupHandler.isTrashDrop(pGroupID, tDropID));
        }
        _mDirty = true;

        if (pPlayer instanceof EntityPlayerMP)
            EnhancedLootBags.NW.sendTo(LootBagStatsSyncMessage.partial(tGroup), (EntityPlayerMP) pPlayer);
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent pEvent) {
        if (pEvent.player instanceof EntityPlayerMP) EnhancedLootBags.NW
                .sendTo(LootBagStatsSyncMessage.full(getStats(pEvent.player)), (EntityPlayerMP) pEvent.player);
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save pEvent) {
        if (!pEvent.world.isRemote && pEvent.world.provider.dimensionId == 0) save();
    }

    public static class OpenRecord {

        private final List<Drop> mDrops = new ArrayList<>();
        private final List<ItemStack> mStacks = new ArrayList<>();

        public void add(Drop pDrop, ItemStack pStack) {
            mDrops.add(pDrop);
            mStacks.add(pStack.copy());
        }
    }
}
