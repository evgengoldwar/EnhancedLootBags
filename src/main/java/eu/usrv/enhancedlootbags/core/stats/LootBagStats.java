package eu.usrv.enhancedlootbags.core.stats;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public class LootBagStats {

    private static final String NBT_GROUPS = "Groups";
    private static final String NBT_GROUP_ID = "ID";
    private static final String NBT_OPENED = "Opened";
    private static final String NBT_DROPS = "Drops";
    private static final String NBT_DROP_ID = "Id";
    private static final String NBT_DROP_STACK = "Stack";
    private static final String NBT_DROP_ITEMS = "Items";
    private static final String NBT_DROP_TIMES = "Times";
    private static final String NBT_DROP_TRASH = "Trash";

    private final Map<Integer, GroupStats> _mGroups = new LinkedHashMap<>();

    public GroupStats getGroup(int pGroupID) {
        return _mGroups.get(pGroupID);
    }

    public GroupStats getOrCreateGroup(int pGroupID) {
        GroupStats tStats = _mGroups.get(pGroupID);
        if (tStats == null) {
            tStats = new GroupStats(pGroupID);
            _mGroups.put(pGroupID, tStats);
        }
        return tStats;
    }

    public void putGroup(GroupStats pStats) {
        _mGroups.put(pStats.getGroupID(), pStats);
    }

    public Collection<GroupStats> getGroups() {
        return _mGroups.values();
    }

    public long getTotalOpened() {
        long tTotal = 0;
        for (GroupStats tGrp : _mGroups.values()) tTotal += tGrp.getOpened();
        return tTotal;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tTag = new NBTTagCompound();
        NBTTagList tGroups = new NBTTagList();
        for (GroupStats tGrp : _mGroups.values()) tGroups.appendTag(tGrp.writeToNBT());
        tTag.setTag(NBT_GROUPS, tGroups);
        return tTag;
    }

    public static LootBagStats readFromNBT(NBTTagCompound pTag) {
        LootBagStats tStats = new LootBagStats();
        NBTTagList tGroups = pTag.getTagList(NBT_GROUPS, 10);
        for (int i = 0; i < tGroups.tagCount(); i++)
            tStats.putGroup(GroupStats.readFromNBT(tGroups.getCompoundTagAt(i)));
        return tStats;
    }

    public static class GroupStats {

        private final int _mGroupID;
        private int _mOpened;
        private final Map<String, DropStats> _mDrops = new LinkedHashMap<>();

        public GroupStats(int pGroupID) {
            _mGroupID = pGroupID;
        }

        public int getGroupID() {
            return _mGroupID;
        }

        public int getOpened() {
            return _mOpened;
        }

        public void incrementOpened() {
            _mOpened++;
        }

        public Collection<DropStats> getDrops() {
            return _mDrops.values();
        }

        public void recordDrop(String pDropID, ItemStack pStack, boolean pTrash) {
            DropStats tDrop = _mDrops.get(pDropID);
            if (tDrop == null) {
                ItemStack tDisplay = pStack.copy();
                tDisplay.stackSize = 1;
                tDrop = new DropStats(pDropID, tDisplay.writeToNBT(new NBTTagCompound()));
                _mDrops.put(pDropID, tDrop);
            }
            tDrop.add(pStack.stackSize);
            tDrop.setTrash(pTrash);
        }

        public NBTTagCompound writeToNBT() {
            NBTTagCompound tTag = new NBTTagCompound();
            tTag.setInteger(NBT_GROUP_ID, _mGroupID);
            tTag.setInteger(NBT_OPENED, _mOpened);
            NBTTagList tDrops = new NBTTagList();
            for (DropStats tDrop : _mDrops.values()) tDrops.appendTag(tDrop.writeToNBT());
            tTag.setTag(NBT_DROPS, tDrops);
            return tTag;
        }

        public static GroupStats readFromNBT(NBTTagCompound pTag) {
            GroupStats tStats = new GroupStats(pTag.getInteger(NBT_GROUP_ID));
            tStats._mOpened = pTag.getInteger(NBT_OPENED);
            NBTTagList tDrops = pTag.getTagList(NBT_DROPS, 10);
            for (int i = 0; i < tDrops.tagCount(); i++) {
                DropStats tDrop = DropStats.readFromNBT(tDrops.getCompoundTagAt(i));
                tStats._mDrops.put(tDrop.getDropID(), tDrop);
            }
            return tStats;
        }
    }

    public static class DropStats {

        private final String _mDropID;
        private final NBTTagCompound _mStackTag;
        private final ItemStack _mDisplayStack;
        private long _mItemCount;
        private int _mTimesDropped;
        private boolean _mTrash;

        private DropStats(String pDropID, NBTTagCompound pStackTag) {
            _mDropID = pDropID;
            _mStackTag = pStackTag;
            _mDisplayStack = ItemStack.loadItemStackFromNBT(pStackTag);
        }

        public String getDropID() {
            return _mDropID;
        }

        public ItemStack getDisplayStack() {
            return _mDisplayStack;
        }

        public long getItemCount() {
            return _mItemCount;
        }

        public int getTimesDropped() {
            return _mTimesDropped;
        }

        public boolean isTrash() {
            return _mTrash;
        }

        public void setTrash(boolean pTrash) {
            _mTrash = pTrash;
        }

        public void add(long pItemCount) {
            _mItemCount += pItemCount;
            _mTimesDropped++;
        }

        public NBTTagCompound writeToNBT() {
            NBTTagCompound tTag = new NBTTagCompound();
            tTag.setString(NBT_DROP_ID, _mDropID);
            tTag.setTag(NBT_DROP_STACK, _mStackTag);
            tTag.setLong(NBT_DROP_ITEMS, _mItemCount);
            tTag.setInteger(NBT_DROP_TIMES, _mTimesDropped);
            tTag.setBoolean(NBT_DROP_TRASH, _mTrash);
            return tTag;
        }

        public static DropStats readFromNBT(NBTTagCompound pTag) {
            DropStats tDrop = new DropStats(pTag.getString(NBT_DROP_ID), pTag.getCompoundTag(NBT_DROP_STACK));
            tDrop._mItemCount = pTag.getLong(NBT_DROP_ITEMS);
            tDrop._mTimesDropped = pTag.getInteger(NBT_DROP_TIMES);
            tDrop._mTrash = pTag.getBoolean(NBT_DROP_TRASH);
            return tDrop;
        }
    }
}
