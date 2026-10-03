package de.sourcemaster.digiminermod.drone;

public enum DroneMode {
	FOLLOW,
	STATIC,
	AUTOMATIC,
	UPGRADE;

	public static DroneMode byId(int id) {
		return values()[Math.floorMod(id, values().length)];
	}
}
