package protocolsupport.protocol;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;

public interface IPipeLineBuilder {

	public DecoderEncoderTuple buildPipeLine(Channel channel, ProtocolVersion version);

	public static class DecoderEncoderTuple {

		private ChannelHandler decoder;
		private ChannelHandler encoder;

		public DecoderEncoderTuple(ChannelHandler decoder, ChannelHandler encoder) {
			this.decoder = decoder;
			this.encoder = encoder;
		}

		public ChannelHandler getDecoder() {
			return decoder;
		}

		public ChannelHandler getEncoder() {
			return encoder;
		}

	}

}
