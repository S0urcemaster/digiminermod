package de.sourcemaster.digiminermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CreditHud {
	private static int balance;
	public static void setBalance(int value) { balance = value; }

	public void extract(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) return;
		String text = "Credits: " + balance;
		int x = minecraft.getWindow().getGuiScaledWidth() - minecraft.font.width(text) - 8;
		graphics.text(minecraft.font, text, x, 8, 0xFFFFD95A, true);
	}
}
