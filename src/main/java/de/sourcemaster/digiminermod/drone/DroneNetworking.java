package de.sourcemaster.digiminermod.drone;

import de.sourcemaster.digiminermod.DigiMinerMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class DroneNetworking {
	private DroneNetworking() {}

	public record OpenPayload(long blockPos, int mode) implements CustomPacketPayload {
		public static final Type<OpenPayload> TYPE = new Type<>(DigiMinerMod.id("open_drone"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.LONG, OpenPayload::blockPos,
				ByteBufCodecs.VAR_INT, OpenPayload::mode, OpenPayload::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record CommandPayload(long blockPos, int command) implements CustomPacketPayload {
		public static final Type<CommandPayload> TYPE = new Type<>(DigiMinerMod.id("drone_command"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CommandPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.LONG, CommandPayload::blockPos,
				ByteBufCodecs.VAR_INT, CommandPayload::command, CommandPayload::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record ProgramPayload(long blockPos, int program, int parameterOne, int parameterTwo, int parameterThree) implements CustomPacketPayload {
		public static final Type<ProgramPayload> TYPE = new Type<>(DigiMinerMod.id("drone_program"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ProgramPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.LONG, ProgramPayload::blockPos,
				ByteBufCodecs.VAR_INT, ProgramPayload::program,
				ByteBufCodecs.VAR_INT, ProgramPayload::parameterOne,
				ByteBufCodecs.VAR_INT, ProgramPayload::parameterTwo,
				ByteBufCodecs.VAR_INT, ProgramPayload::parameterThree,
				ProgramPayload::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record RenamePayload(long blockPos, String name) implements CustomPacketPayload {
		public static final Type<RenamePayload> TYPE = new Type<>(DigiMinerMod.id("rename_drone"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RenamePayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.LONG, RenamePayload::blockPos,
				ByteBufCodecs.stringUtf8(16), RenamePayload::name,
				RenamePayload::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record CreditPayload(int balance) implements CustomPacketPayload {
		public static final Type<CreditPayload> TYPE = new Type<>(DigiMinerMod.id("credit_balance"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CreditPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, CreditPayload::balance, CreditPayload::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record ScannerPayload(long blockPos, boolean ironNearby, boolean diamondNearby) implements CustomPacketPayload {
		public static final Type<ScannerPayload> TYPE = new Type<>(DigiMinerMod.id("scanner_status"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ScannerPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.LONG, ScannerPayload::blockPos,
				ByteBufCodecs.BOOL, ScannerPayload::ironNearby,
				ByteBufCodecs.BOOL, ScannerPayload::diamondNearby,
				ScannerPayload::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public static void registerServer() {
		PayloadTypeRegistry.clientboundPlay().register(OpenPayload.TYPE, OpenPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CreditPayload.TYPE, CreditPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScannerPayload.TYPE, ScannerPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CommandPayload.TYPE, CommandPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ProgramPayload.TYPE, ProgramPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RenamePayload.TYPE, RenamePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CommandPayload.TYPE, (payload, context) -> {
			var pos = net.minecraft.core.BlockPos.of(payload.blockPos());
			if (!(context.player().level().getBlockEntity(pos) instanceof DroneBlockEntity drone)
					|| !drone.isOwner(context.player()) || !pos.closerToCenterThan(context.player().position(), 16.0)) return;
			if (payload.command() >= 0 && payload.command() < DroneMode.values().length) {
				drone.setMode(DroneMode.byId(payload.command()));
				return;
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(ProgramPayload.TYPE, (payload, context) -> {
			var pos = net.minecraft.core.BlockPos.of(payload.blockPos());
			if (!(context.player().level().getBlockEntity(pos) instanceof DroneBlockEntity drone)
					|| !drone.isOwner(context.player()) || !pos.closerToCenterThan(context.player().position(), 16.0)) return;
			boolean started = drone.startProgram(payload.program(), payload.parameterOne(), payload.parameterTwo(), payload.parameterThree());
			context.player().sendOverlayMessage(net.minecraft.network.chat.Component.literal(started
					? "Digi: Program started" : "Digi: Program cannot start"));
		});
		ServerPlayNetworking.registerGlobalReceiver(RenamePayload.TYPE, (payload, context) -> {
			var pos = net.minecraft.core.BlockPos.of(payload.blockPos());
			if (!(context.player().level().getBlockEntity(pos) instanceof DroneBlockEntity drone)
					|| !drone.isOwner(context.player()) || !pos.closerToCenterThan(context.player().position(), 16.0)) return;
			drone.setDroneName(payload.name());
		});
	}

	public static void open(ServerPlayer player, DroneBlockEntity drone) {
		ServerPlayNetworking.send(player, new OpenPayload(drone.getBlockPos().asLong(), drone.getMode().ordinal()));
	}
}
