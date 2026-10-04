package de.sourcemaster.digiminermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

public final class ScannerHud {
	private static BlockPos dronePosition;
	private static boolean ironNearby;
	private static long validUntil;

	public static void update(long packedPosition, boolean iron) {
		dronePosition = BlockPos.of(packedPosition);
		ironNearby = iron;
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
	}
}
