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
		} catch (IOException ignored) {
			// Keep defaults if an existing config cannot be read.
		}
		return config;
	}

	private void save() {
		Properties properties = new Properties();
		properties.setProperty("showIngameHints", Boolean.toString(this.showIngameHints));
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				properties.store(writer, "Digi Miner Mod options");
			}
		} catch (IOException ignored) {
			// A failed save must not interrupt the game.
		}
	}
}
