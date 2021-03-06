package uk.co.shadeddimensions.ep3.network.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetworkManager;
import net.minecraft.tileentity.TileEntity;
import uk.co.shadeddimensions.ep3.network.CommonProxy;
import uk.co.shadeddimensions.ep3.tileentity.TileEnhancedPortals;
import cpw.mods.fml.common.network.Player;

public class PacketRequestData extends PacketEnhancedPortals
{
    int x, y, z;

    public PacketRequestData()
    {

    }

    public PacketRequestData(TileEnhancedPortals tile)
    {
        x = tile.xCoord;
        y = tile.yCoord;
        z = tile.zCoord;
        isChunkDataPacket = true;
    }

    @Override
    public void readPacketData(DataInputStream stream) throws IOException
    {
        x = stream.readInt();
        y = stream.readInt();
        z = stream.readInt();
    }

    @Override
    public void writePacketData(DataOutputStream stream) throws IOException
    {
        stream.writeInt(x);
        stream.writeInt(y);
        stream.writeInt(z);
    }

    @Override
    public void serverPacket(INetworkManager manager, PacketEnhancedPortals packet, Player player)
    {
        TileEntity tile = ((EntityPlayer) player).worldObj.getBlockTileEntity(x, y, z);

        if (tile != null && tile instanceof TileEnhancedPortals)
        {
            CommonProxy.sendUpdatePacketToPlayer((TileEnhancedPortals) tile, (EntityPlayer) player);
        }
    }
}
