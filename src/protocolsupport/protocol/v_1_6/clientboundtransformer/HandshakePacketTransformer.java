package protocolsupport.protocol.v_1_6.clientboundtransformer;

import io.netty.channel.Channel;

import java.io.IOException;

import net.minecraft.server.v1_8_R1.Packet;
import protocolsupport.protocol.PacketDataSerializer;

public class HandshakePacketTransformer implements PacketTransformer {

	@Override
	public void tranform(Channel channel, int packetId, Packet packet, PacketDataSerializer serializer) throws IOException {
	}

}
