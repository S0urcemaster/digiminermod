package de.sourcemaster.digiminermod.client;

import de.sourcemaster.digiminermod.DigiMinerMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.tags.BlockTags;

import java.util.Locale;

public final class ScannerHud {
	private static final int CHUNK_RADIUS = 12;
	private long nextScanTime;
	private BlockPos nearestSpawner;
	private long nextIronScanTime;
	private boolean ironNearby;

	public void extract(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || minecraft.gui.screen() != null) {
			return;
		}
		boolean holdingIronScanner = minecraft.player.getMainHandItem().is(DigiMinerMod.IRON_SCANNER)
				|| minecraft.player.getOffhandItem().is(DigiMinerMod.IRON_SCANNER);
		if (holdingIronScanner) {
			long now = System.currentTimeMillis();
			if (now >= this.nextIronScanTime) {
				this.ironNearby = hasNearbyIron(minecraft);
				this.nextIronScanTime = now + 250L;
			}
			graphics.text(minecraft.font, "Iron Scanner: " + (this.ironNearby ? "Beep" : "None"),
					8, 8, this.ironNearby ? 0xFFFFB85C : 0xFFB8C4D6, true);
			return;
		}
		if (!minecraft.player.getMainHandItem().is(DigiMinerMod.SPAWNER_SCANNER)
				&& !minecraft.player.getOffhandItem().is(DigiMinerMod.SPAWNER_SCANNER)) return;

		long now = System.currentTimeMillis();
		if (now >= this.nextScanTime) {
			this.nearestSpawner = this.findNearestSpawner(minecraft);
			this.nextScanTime = now + 1000L;
		}

		BlockPos player = minecraft.player.blockPosition();
		BlockPos spawn = minecraft.level.getRespawnData().pos();
		double spawnDistance = horizontalDistance(player, spawn);
		long dayTime = minecraft.level.getLevelData().getGameTime() % 24000L;
		long minutes = ((dayTime + 6000L) % 24000L) * 1440L / 24000L;
		String clock = String.format(Locale.ROOT, "%02d:%02d", minutes / 60L, minutes % 60L);

		int x = 8;
		int y = 8;
		graphics.text(minecraft.font, "Height: " + player.getY(), x, y, 0xFFFFFFFF, true);
		graphics.text(minecraft.font, "Spawner: " + (this.nearestSpawner == null ? "No signal"
				: direction(player, this.nearestSpawner)), x, y + 11, 0xFFFFB85C, true);
		graphics.text(minecraft.font, "Spawn: " + direction(player, spawn) + "  " + Math.round(spawnDistance) + " blocks",
				x, y + 22, 0xFFB8E2FF, true);
		graphics.text(minecraft.font, "Time: " + clock, x, y + 33, 0xFFFFFFFF, true);
	}

	private static boolean hasNearbyIron(Minecraft minecraft) {
		BlockPos origin = minecraft.player.blockPosition();
		for (int x = -3; x <= 3; x++) {
			for (int y = -3; y <= 3; y++) {
				for (int z = -3; z <= 3; z++) {
					if (x * x + y * y + z * z > 9) continue;
					if (minecraft.level.getBlockState(origin.offset(x, y, z)).is(BlockTags.IRON_ORES)) return true;
				}
			}
		}
		return false;
	}

	private BlockPos findNearestSpawner(Minecraft minecraft) {
		BlockPos origin = minecraft.player.blockPosition();
		int centerChunkX = origin.getX() >> 4;
		int centerChunkZ = origin.getZ() >> 4;
		BlockPos nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (int x = centerChunkX - CHUNK_RADIUS; x <= centerChunkX + CHUNK_RADIUS; x++) {
			for (int z = centerChunkZ - CHUNK_RADIUS; z <= centerChunkZ + CHUNK_RADIUS; z++) {
				LevelChunk chunk = minecraft.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
				if (chunk == null) continue;
				for (var entry : chunk.getBlockEntities().entrySet()) {
					if (!(entry.getValue() instanceof SpawnerBlockEntity)) continue;
					double distance = entry.getKey().distSqr(origin);
					if (distance < nearestDistance) {
						nearestDistance = distance;
						nearest = entry.getKey().immutable();
					}
				}
			}
		}
		return nearest;
	}

	private static double horizontalDistance(BlockPos from, BlockPos to) {
		double x = to.getX() - from.getX();
		double z = to.getZ() - from.getZ();
		return Math.sqrt(x * x + z * z);
	}

	private static String direction(BlockPos from, BlockPos to) {
		double angle = Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ()));
		String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		return names[Math.floorMod((int) Math.round(angle / (Math.PI / 4.0)), names.length)];
	}
}
