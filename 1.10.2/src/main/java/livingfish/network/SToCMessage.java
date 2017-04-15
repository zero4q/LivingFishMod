package livingfish.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class SToCMessage implements IMessage {

	private byte[] data;
	
	public SToCMessage() {
		this(new byte[] { 0 });
	}
	
	public SToCMessage(ByteBuf dataToSet) {
		this(dataToSet.array());
	}
	
	public SToCMessage(byte[] dataToSet) {
		if (dataToSet.length > 2097136) {
			throw new IllegalArgumentException("Payload may not be larger than 2097136 (0x1ffff0) bytes");
		}
		this.data = dataToSet;
	}

	@Override 
	public void toBytes(ByteBuf buf) {
		if (this.data.length > 2097136) {
			throw new IllegalArgumentException("Payload may not be larger than 2097136 (0x1ffff0) bytes");
	    }
		buf.writeShort(this.data.length);
		buf.writeBytes(this.data);
	}

	@Override 
	public void fromBytes(ByteBuf buf) {
		this.data = new byte[buf.readShort()];
	    buf.readBytes(this.data);
	}
	
	public byte[] getData() {
		return this.data;
	}
	
}
