package protocolsupport.protocol.v_1_6;

import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.SecretKey;

import net.minecraft.server.v1_8_R1.ChatComponentText;
import net.minecraft.server.v1_8_R1.EntityPlayer;
import net.minecraft.server.v1_8_R1.IChatBaseComponent;
import net.minecraft.server.v1_8_R1.MinecraftEncryption;
import net.minecraft.server.v1_8_R1.MinecraftServer;
import net.minecraft.server.v1_8_R1.NetworkManager;
import net.minecraft.server.v1_8_R1.PacketLoginInEncryptionBegin;
import net.minecraft.server.v1_8_R1.PacketLoginInStart;
import net.minecraft.server.v1_8_R1.PacketLoginOutDisconnect;
import net.minecraft.server.v1_8_R1.PacketLoginOutEncryptionBegin;
import net.minecraft.server.v1_8_R1.PacketLoginOutSuccess;

import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import protocolsupport.protocol.ProtocolVersion;
import protocolsupport.protocol.v_1_6.serverboundtransformer.PacketDecoder;

import com.google.common.base.Charsets;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

public class LoginListener extends net.minecraft.server.v1_8_R1.LoginListener {

	protected static final Logger logger = LogManager.getLogger();
	private static final AtomicInteger authThreadsCounter = new AtomicInteger(0);
	private static final Random random = new Random();

	private PacketDecoder decoder;
	private ProtocolVersion version;
	private final byte[] randomBytes = new byte[4];
	private int loginTicks;
	protected SecretKey loginKey;
	protected LoginState state = LoginState.HELLO;
	protected GameProfile profile;
	protected String serverId = "";

	public LoginListener(PacketDecoder decoder, NetworkManager networkmanager, ProtocolVersion version) {
		super(MinecraftServer.getServer(), networkmanager);
		random.nextBytes(randomBytes);
		this.decoder = decoder;
		this.version = version;
	}

	@Override
	public void c() {
		if (state == LoginState.READY_TO_ACCEPT) {
			b();
		}
		if (loginTicks++ == 600) {
			disconnect("Took too long to log in");
		}
	}

	@Override
	public void disconnect(final String s) {
		try {
			LoginListener.logger.info("Disconnecting " + d() + ": " + s);
			final ChatComponentText chatcomponenttext = new ChatComponentText(s);
			networkManager.handle(new PacketLoginOutDisconnect(chatcomponenttext));
			networkManager.close(chatcomponenttext);
		} catch (Exception exception) {
			LoginListener.logger.error("Error whilst disconnecting player", exception);
		}
	}

	@Override
	public void initUUID() {
		UUID uuid;
		if (networkManager.spoofedUUID != null) {
			uuid = networkManager.spoofedUUID;
		} else {
			uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + profile.getName()).getBytes(Charsets.UTF_8));
		}
		profile = new GameProfile(uuid, profile.getName());
		if (networkManager.spoofedProfile != null) {
			for (final Property property : networkManager.spoofedProfile) {
				profile.getProperties().put(property.getName(), property);
			}
		}
	}

	@Override
	public void b() {
		final EntityPlayer player = MinecraftServer.getServer().getPlayerList().attemptLogin(this, profile, hostname);
		if (player != null) {
			state = LoginState.ACCEPTED;
			networkManager.handle(new PacketLoginOutSuccess(profile));
			MinecraftServer.getServer().getPlayerList().a(networkManager, MinecraftServer.getServer().getPlayerList().processLogin(profile, player));
			networkManager.spoofedUUID = version.getUUID();
		}
	}

	@Override
	public void a(final IChatBaseComponent ichatbasecomponent) {
		LoginListener.logger.info(d() + " lost connection: " + ichatbasecomponent.c());
	}

	@Override
	public String d() {
		return (profile != null) ? (profile.toString() + " (" + networkManager.getSocketAddress().toString() + ")") : String.valueOf(networkManager.getSocketAddress());
	}

	@Override
	public void a(final PacketLoginInStart packetlogininstart) {
		Validate.validState(state == LoginState.HELLO, "Unexpected hello packet", new Object[0]);
		profile = packetlogininstart.a();
		if (MinecraftServer.getServer().getOnlineMode() && !networkManager.c()) {
			state = LoginState.KEY;
			networkManager.handle(new PacketLoginOutEncryptionBegin(serverId, MinecraftServer.getServer().P().getPublic(), randomBytes));
		} else {
			new ThreadPlayerLookupUUID(this, "User Authenticator #" + LoginListener.authThreadsCounter.incrementAndGet()).start();
		}
	}

	@Override
	public void a(final PacketLoginInEncryptionBegin packetlogininencryptionbegin) {
		Validate.validState(state == LoginState.KEY, "Unexpected key packet", new Object[0]);
		final PrivateKey privatekey = MinecraftServer.getServer().P().getPrivate();
		if (!Arrays.equals(randomBytes, packetlogininencryptionbegin.b(privatekey))) {
			throw new IllegalStateException("Invalid nonce!");
		}
		loginKey = packetlogininencryptionbegin.a(privatekey);
		state = LoginState.AUTHENTICATING;
		decoder.attachDecryptor(MinecraftEncryption.a(2, loginKey));
		new ThreadPlayerLookupUUID(this, "User Authenticator #" + LoginListener.authThreadsCounter.incrementAndGet()).start();
	}

	@Override
	protected GameProfile a(final GameProfile gameprofile) {
		final UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + gameprofile.getName()).getBytes(Charsets.UTF_8));
		return new GameProfile(uuid, gameprofile.getName());
	}

	protected static enum LoginState {
		HELLO, KEY, AUTHENTICATING, READY_TO_ACCEPT, ACCEPTED;
	}

}