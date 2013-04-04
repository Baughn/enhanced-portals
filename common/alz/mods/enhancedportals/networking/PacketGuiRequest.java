package alz.mods.enhancedportals.networking;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.network.packet.Packet250CustomPayload;
import alz.mods.enhancedportals.reference.Reference;

public class PacketGuiRequest
{
	public int guiID, xCoord, yCoord, zCoord;
	
	public PacketGuiRequest()
	{
	}

	public PacketGuiRequest(int GUIID, int x, int y, int z)
	{
		guiID = GUIID;
		xCoord = x;
		yCoord = y;
		zCoord = z;
	}

	public int getPacketID()
	{
		return Reference.Networking.GuiRequest;
	}

	public Packet250CustomPayload getPacket()
	{
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		DataOutputStream dataStream = new DataOutputStream(byteStream);
		Packet250CustomPayload packet = new Packet250CustomPayload();

		try
		{
			dataStream.writeByte(getPacketID());
			addPacketData(dataStream);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		packet.channel = Reference.MOD_ID;
		packet.data = byteStream.toByteArray();
		packet.length = packet.data.length;
		packet.isChunkDataPacket = true;

		return packet;
	}

	public void getPacketData(DataInputStream stream) throws IOException
	{
		guiID = stream.readInt();
		xCoord = stream.readInt();
		yCoord = stream.readInt();
		zCoord = stream.readInt();
	}

	public void addPacketData(DataOutputStream stream) throws IOException
	{
		stream.writeInt(guiID);
		stream.writeInt(xCoord);
		stream.writeInt(yCoord);
		stream.writeInt(zCoord);
	}
}
