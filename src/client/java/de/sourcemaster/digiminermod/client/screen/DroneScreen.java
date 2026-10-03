package de.sourcemaster.digiminermod.client.screen;

import de.sourcemaster.digiminermod.client.input.ControllerSupport;
import de.sourcemaster.digiminermod.drone.DroneMode;
import de.sourcemaster.digiminermod.drone.DroneNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DroneScreen extends Screen {
	private static final String[] PAGES = {"Inventory", "Mode", "Configuration"};
	private static final String[] MODES = {"Follow", "Static", "Automatic", "Upgrade"};
	private static final String[] MOVES = {"Forward", "Backward", "Up", "Down", "Turn left", "Turn right"};
	private final long blockPos;
	private int mode;
	private int page = 1;
	private int cargoPage;
	private int cursor;
	private boolean previousA;
	private boolean previousLb;
	private boolean previousRb;
	private boolean previousLt;
	private boolean previousRt;

	public DroneScreen(long blockPos, int mode) {
		super(Component.literal("Drone"));
		this.blockPos = blockPos;
		this.mode = mode;
		this.cursor = mode;
	}

	@Override
	public void tick() {
		ControllerSupport.Snapshot pad = ControllerSupport.poll();
		if (!pad.connected()) return;
		if (pad.leftBumper() && !this.previousLb) { this.page = Math.floorMod(this.page - 1, 3); this.cursor = this.page == 1 ? this.mode : 0; }
		if (pad.rightBumper() && !this.previousRb) { this.page = (this.page + 1) % 3; this.cursor = this.page == 1 ? this.mode : 0; }
		this.previousLb = pad.leftBumper();
		this.previousRb = pad.rightBumper();
		if (this.page == 0) {
			boolean lt = pad.leftTrigger() > 0.5F;
			boolean rt = pad.rightTrigger() > 0.5F;
			if ((lt && !this.previousLt) || (rt && !this.previousRt)) this.cargoPage = 1 - this.cargoPage;
			this.previousLt = lt;
			this.previousRt = rt;
		}
		int count = this.page == 0 ? (this.cargoPage == 0 ? 14 : 13)
				: this.page == 1 ? MODES.length
				: this.mode == DroneMode.STATIC.ordinal() ? MOVES.length : 0;
		float x = pad.leftX(), y = pad.leftY();
		if (count > 0 && x * x + y * y > 0.25F) {
			double angle = Math.atan2(x, -y);
			if (angle < 0) angle += Math.PI * 2.0;
			this.cursor = Math.floorMod((int) Math.round(angle / (Math.PI * 2.0 / count)), count);
		}
		if (pad.a() && !this.previousA) this.activate();
		this.previousA = pad.a();
	}

	private void activate() {
		if (this.page == 1) {
			this.mode = this.cursor;
			ClientPlayNetworking.send(new DroneNetworking.CommandPayload(this.blockPos, this.mode));
		} else if (this.page == 2 && this.mode == DroneMode.STATIC.ordinal()) {
			ClientPlayNetworking.send(new DroneNetworking.CommandPayload(this.blockPos, 4 + this.cursor));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractMenuBackground(graphics);
		graphics.centeredText(this.font, "[LB]  " + PAGES[this.page] + "  [RB]", this.width / 2, 22, 0xFFFFFFFF);
		if (this.page == 0) {
			int count = this.cargoPage == 0 ? 14 : 13;
			for (int i = 0; i < count; i++) this.radialSlot(graphics, "", i, count, false);
			graphics.centeredText(this.font, (this.cargoPage + 1) + "/2", this.width / 2, this.height / 2 - 4, 0xFFB8C4D6);
			graphics.centeredText(this.font, "[LT]  [RT]", this.width / 2, this.height / 2 + 10, 0xFF7F8A99);
			graphics.centeredText(this.font, "Cargo transfer comes with the next step", this.width / 2, 148, 0xFF7F8A99);
		} else if (this.page == 1) {
			for (int i = 0; i < MODES.length; i++) this.radialSlot(graphics, (i == this.mode ? "(*) " : "( ) ") + MODES[i], i, MODES.length, i == this.mode);
		} else if (this.mode == DroneMode.STATIC.ordinal()) {
			for (int i = 0; i < MOVES.length; i++) this.radialSlot(graphics, MOVES[i], i, MOVES.length, false);
		} else {
			String message = this.mode == DroneMode.FOLLOW.ordinal() ? "Following player"
					: this.mode == DroneMode.AUTOMATIC.ordinal() ? "No program installed" : "No upgrades installed";
			graphics.centeredText(this.font, message, this.width / 2, 82, 0xFFB8C4D6);
		}
		graphics.centeredText(this.font, "[A] Select/action", this.width / 2, this.height - 28, 0xFFB8C4D6);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void radialSlot(GuiGraphicsExtractor graphics, String label, int index, int count, boolean active) {
		double angle = Math.PI * 2.0 * index / count - Math.PI / 2.0;
		int x = this.width / 2 + (int) Math.round(Math.cos(angle) * 92.0) - 36;
		int y = this.height / 2 + (int) Math.round(Math.sin(angle) * 66.0) - 10;
		boolean selected = this.cursor == index;
		graphics.fill(x, y, x + 72, y + 20, selected ? 0xDD28313D : 0xB010141A);
		graphics.outline(x, y, 72, 20, selected ? 0xFFF4D35E : active ? 0xFF65CFFF : 0x906E747C);
		graphics.centeredText(this.font, label, x + 36, y + 6,
				active ? 0xFF65CFFF : 0xFFFFFFFF);
	}

	@Override public boolean isPauseScreen() { return false; }
}
