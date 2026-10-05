package de.sourcemaster.digiminermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

public final class ScannerHud {
	private static BlockPos dronePosition;
	private static boolean ironNearby;
	private static int spawnDeltaX;
	private static int spawnDeltaY;
	private static int spawnDeltaZ;
	private static long validUntil;

	public static void update(long packedPosition, boolean iron, int deltaX, int deltaY, int deltaZ) {
		dronePosition = BlockPos.of(packedPosition);
		ironNearby = iron;
		spawnDeltaX = deltaX;
		spawnDeltaY = deltaY;
		spawnDeltaZ = deltaZ;
		validUntil = System.currentTimeMillis() + 2500L;
	}

	public void extract(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || minecraft.gui.screen() != null
				|| dronePosition == null || System.currentTimeMillis() > validUntil) return;
		graphics.text(minecraft.font, "Digi: " + dronePosition.getX() + "  " + dronePosition.getY() + "  "
				+ dronePosition.getZ(), 8, 8, 0xFFFFD95A, true);
		graphics.text(minecraft.font, "Iron: " + (ironNearby ? "Beep" : "Nothing"), 8, 19,
				ironNearby ? 0xFFFFB85C : 0xFFB8C4D6, true);
		graphics.text(minecraft.font, "Spawn delta: " + signed(spawnDeltaX) + "  " + signed(spawnDeltaY)
				+ "  " + signed(spawnDeltaZ), 8, 30, 0xFFFFD95A, true);
	}

	private static String signed(int value) { return value >= 0 ? "+" + value : Integer.toString(value); }
}
