package de.sourcemaster.digiminermod.client.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class DigiMinerConfig {
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("digiminermod.properties");
	private static final DigiMinerConfig INSTANCE = load();

	private boolean showIngameHints = true;
	private double moveDeadzone = 0.18;
	private double lookDeadzone = 0.20;
	private double lookSpeedHorizontal = 240.0;
	private double lookSpeedVertical = 180.0;
	private double lookAccelerationHorizontal = 1200.0;
	private double lookAccelerationVertical = 1200.0;
	private boolean invertLookY;

	private DigiMinerConfig() {
	}

	public static DigiMinerConfig get() {
		return INSTANCE;
	}

	public boolean showIngameHints() {
		return this.showIngameHints;
	}

	public void setShowIngameHints(boolean value) {
		this.showIngameHints = value;
		this.save();
	}

	public double moveDeadzone() { return this.moveDeadzone; }
	public double lookDeadzone() { return this.lookDeadzone; }
	public double lookSpeedHorizontal() { return this.lookSpeedHorizontal; }
	public double lookSpeedVertical() { return this.lookSpeedVertical; }
	public double lookAccelerationHorizontal() { return this.lookAccelerationHorizontal; }
	public double lookAccelerationVertical() { return this.lookAccelerationVertical; }
	public boolean invertLookY() { return this.invertLookY; }

	public void setMoveDeadzone(double value) { this.moveDeadzone = clamp(value, 0.0, 0.5); this.save(); }
	public void setLookDeadzone(double value) { this.lookDeadzone = clamp(value, 0.0, 0.5); this.save(); }
	public void setLookSpeedHorizontal(double value) { this.lookSpeedHorizontal = clamp(value, 45.0, 720.0); this.save(); }
	public void setLookSpeedVertical(double value) { this.lookSpeedVertical = clamp(value, 45.0, 720.0); this.save(); }
	public void setLookAccelerationHorizontal(double value) { this.lookAccelerationHorizontal = clamp(value, 90.0, 3600.0); this.save(); }
	public void setLookAccelerationVertical(double value) { this.lookAccelerationVertical = clamp(value, 90.0, 3600.0); this.save(); }
	public void setInvertLookY(boolean value) { this.invertLookY = value; this.save(); }

	private static DigiMinerConfig load() {
		DigiMinerConfig config = new DigiMinerConfig();
		if (!Files.isRegularFile(PATH)) {
			return config;
		}

		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(PATH)) {
			properties.load(reader);
			config.showIngameHints = Boolean.parseBoolean(
					properties.getProperty("showIngameHints", "true"));
			config.moveDeadzone = readDouble(properties, "moveDeadzone", 0.18, 0.0, 0.5);
			config.lookDeadzone = readDouble(properties, "lookDeadzone", 0.20, 0.0, 0.5);
			double legacySensitivity = readLookSensitivity(properties);
			config.lookSpeedHorizontal = readDouble(properties, "lookSpeedHorizontal", legacySensitivity, 45.0, 720.0);
			config.lookSpeedVertical = readDouble(properties, "lookSpeedVertical", legacySensitivity, 45.0, 720.0);
			config.lookAccelerationHorizontal = readDouble(properties, "lookAccelerationHorizontal", 1200.0, 90.0, 3600.0);
			config.lookAccelerationVertical = readDouble(properties, "lookAccelerationVertical", 1200.0, 90.0, 3600.0);
			config.invertLookY = Boolean.parseBoolean(properties.getProperty("invertLookY", "false"));
		} catch (IOException ignored) {
			// Keep defaults if an existing config cannot be read.
		}
		return config;
	}

	private void save() {
		Properties properties = new Properties();
		properties.setProperty("showIngameHints", Boolean.toString(this.showIngameHints));
		properties.setProperty("moveDeadzone", Double.toString(this.moveDeadzone));
		properties.setProperty("lookDeadzone", Double.toString(this.lookDeadzone));
		properties.setProperty("lookSpeedHorizontal", Double.toString(this.lookSpeedHorizontal));
		properties.setProperty("lookSpeedVertical", Double.toString(this.lookSpeedVertical));
		properties.setProperty("lookAccelerationHorizontal", Double.toString(this.lookAccelerationHorizontal));
		properties.setProperty("lookAccelerationVertical", Double.toString(this.lookAccelerationVertical));
		properties.setProperty("invertLookY", Boolean.toString(this.invertLookY));
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				properties.store(writer, "Digi Miner Mod options");
			}
		} catch (IOException ignored) {
			// A failed save must not interrupt the game.
		}
	}

	private static double readDouble(Properties properties, String key, double fallback, double min, double max) {
		try {
			return clamp(Double.parseDouble(properties.getProperty(key, Double.toString(fallback))), min, max);
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static double readLookSensitivity(Properties properties) {
		try {
			double value = Double.parseDouble(properties.getProperty("lookSensitivity", "180.0"));
			// Migrate the former 0.1x-3.0x scale to degrees per second.
			if (value <= 3.0) {
				value *= 60.0;
			}
			return clamp(value, 45.0, 360.0);
		} catch (NumberFormatException ignored) {
			return 180.0;
		}
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
