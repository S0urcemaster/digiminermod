package de.sourcemaster.digiminermod.credit;

import de.sourcemaster.digiminermod.drone.DroneNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import java.util.UUID;

public final class CreditAccount {
	private static final String OBJECTIVE = "digiminer_credits";
	private CreditAccount() {}

	private static Objective objective(MinecraftServer server) {
		var scoreboard = server.getScoreboard();
		Objective objective = scoreboard.getObjective(OBJECTIVE);
		if (objective == null) objective = scoreboard.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY,
				Component.literal("Credits"), ObjectiveCriteria.RenderType.INTEGER, false, null);
		return objective;
	}

	public static int balance(ServerPlayer player) {
		return player.level().getServer().getScoreboard()
				.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.getUUID().toString()), objective(player.level().getServer())).get();
	}

	public static void add(ServerPlayer player, int credits) {
		add(player.level().getServer(), player.getUUID(), credits);
	}

	public static void add(MinecraftServer server, UUID ownerId, int credits) {
		var score = server.getScoreboard()
				.getOrCreatePlayerScore(ScoreHolder.forNameOnly(ownerId.toString()), objective(server));
		score.add(credits);
		ServerPlayer online = server.getPlayerList().getPlayer(ownerId);
		if (online != null) sync(online);
	}

	public static void sync(ServerPlayer player) {
		ServerPlayNetworking.send(player, new DroneNetworking.CreditPayload(balance(player)));
	}
}
