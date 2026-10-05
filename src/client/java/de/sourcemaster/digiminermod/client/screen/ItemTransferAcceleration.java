package de.sourcemaster.digiminermod.client.screen;

import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;

/** Tick-based repeat acceleration shared by all controller inventory screens. */
final class ItemTransferAcceleration {
	private static final int INITIAL_REPEAT_DELAY_TICKS = 6;
	private int heldTicks;

	int amount(boolean pressed, boolean wasPressed) {
		if (!pressed) {
			this.heldTicks = 0;
			return 0;
		}
		if (!wasPressed) {
			this.heldTicks = 0;
			return 1;
		}
		this.heldTicks++;
		if (this.heldTicks <= INITIAL_REPEAT_DELAY_TICKS) return 0;
		return 1 + this.heldTicks / DigiMinerConfig.get().itemTransferAccelerationTicks();
	}
}
