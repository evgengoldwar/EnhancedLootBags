package eu.usrv.enhancedlootbags.net.msg;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import eu.usrv.enhancedlootbags.EnhancedLootBags;
import eu.usrv.enhancedlootbags.client.ClientLootBagStats;
import eu.usrv.enhancedlootbags.core.stats.LootBagStats;
import eu.usrv.enhancedlootbags.core.stats.LootBagStats.GroupStats;
import eu.usrv.yamcore.network.client.AbstractClientMessageHandler;
import io.netty.buffer.ByteBuf;

public class LootBagStatsSyncMessage implements IMessage {

    protected boolean _mFullSync;
    protected NBTTagCompound _mPayload;

    public LootBagStatsSyncMessage() {}

    private LootBagStatsSyncMessage(boolean pFullSync, NBTTagCompound pPayload) {
        _mFullSync = pFullSync;
        _mPayload = pPayload;
    }

    public static LootBagStatsSyncMessage full(LootBagStats pStats) {
        return new LootBagStatsSyncMessage(true, pStats.writeToNBT());
    }

    public static LootBagStatsSyncMessage partial(GroupStats pGroup) {
        return new LootBagStatsSyncMessage(false, pGroup.writeToNBT());
    }

    @Override
    public void fromBytes(ByteBuf pBuffer) {
        _mFullSync = pBuffer.readBoolean();
        byte[] tData = new byte[pBuffer.readInt()];
        pBuffer.readBytes(tData);
        try {
            _mPayload = CompressedStreamTools.readCompressed(new ByteArrayInputStream(tData));
        } catch (IOException e) {
            EnhancedLootBags.Logger.error("[LootBags] Received invalid statistics from server");
            _mPayload = null;
        }
    }

    @Override
    public void toBytes(ByteBuf pBuffer) {
        pBuffer.writeBoolean(_mFullSync);
        try {
            byte[] tData = CompressedStreamTools.compress(_mPayload);
            pBuffer.writeInt(tData.length);
            pBuffer.writeBytes(tData);
        } catch (IOException e) {
            throw new RuntimeException("Unable to serialize lootbag statistics", e);
        }
    }

    public static class LootBagStatsSyncMessageHandler extends AbstractClientMessageHandler<LootBagStatsSyncMessage> {

        @Override
        public IMessage handleClientMessage(EntityPlayer pPlayer, LootBagStatsSyncMessage pMessage,
                MessageContext pCtx) {
            if (pMessage._mPayload == null) return null;

            if (pMessage._mFullSync) ClientLootBagStats.setStats(LootBagStats.readFromNBT(pMessage._mPayload));
            else ClientLootBagStats.updateGroup(GroupStats.readFromNBT(pMessage._mPayload));
            return null;
        }
    }
}
