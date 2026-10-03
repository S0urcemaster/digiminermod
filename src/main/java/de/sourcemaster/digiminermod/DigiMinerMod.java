package de.sourcemaster.digiminermod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DigiMinerMod implements ModInitializer {
	public static final String MOD_ID = "digiminermod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			var player = listener.player;
			String starterTag = MOD_ID + ".received_starter_tools";
			if (!player.isCreative() && !player.isSpectator() && !player.entityTags().contains(starterTag)) {
				giveOrDrop(player, new ItemStack(Items.IRON_AXE));
				giveOrDrop(player, new ItemStack(Items.IRON_PICKAXE));
				player.addTag(starterTag);
			}
		});
		LOGGER.info("Digi Miner Mod wurde initialisiert.");
	}

	private static void giveOrDrop(net.minecraft.world.entity.player.Player player, ItemStack stack) {
		if (!player.addItem(stack)) {
			player.drop(stack, false);
		}
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
