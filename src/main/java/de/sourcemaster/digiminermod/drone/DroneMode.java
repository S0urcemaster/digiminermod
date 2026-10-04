package de.sourcemaster.digiminermod.drone;

public enum DroneMode {
	FOLLOW,
	STATIC,
	BUILD,
	EXCAVATE;

	public static DroneMode byId(int id) {
		return values()[Math.floorMod(id, values().length)];
	}
}
