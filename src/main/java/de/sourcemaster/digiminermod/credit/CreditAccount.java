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
import net.fabricmc.loader.api.FabricLoader;

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

	/** Recovers scores written for Loom's former random Player### identities. */
	public static void migrateDevelopmentAccounts(ServerPlayer player) {
		if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
		var scoreboard = player.level().getServer().getScoreboard();
		Objective objective = objective(player.level().getServer());
		String currentOwner = player.getUUID().toString();
		int recovered = 0;
		for (var entry : java.util.List.copyOf(scoreboard.listPlayerScores(objective))) {
			if (entry.owner().equals(currentOwner) || entry.value() <= 0) continue;
			try {
				UUID.fromString(entry.owner());
			} catch (IllegalArgumentException ignored) {
				continue;
			}
			recovered += entry.value();
			scoreboard.resetSinglePlayerScore(ScoreHolder.forNameOnly(entry.owner()), objective);
		}
		if (recovered > 0) scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(currentOwner), objective).add(recovered);
	}
}
