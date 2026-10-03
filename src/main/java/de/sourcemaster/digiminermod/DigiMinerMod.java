package de.sourcemaster.digiminermod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DigiMinerMod implements ModInitializer {
	public static final String MOD_ID = "digiminermod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final ResourceKey<Item> SPAWNER_SCANNER_KEY = ResourceKey.create(Registries.ITEM, id("spawner_scanner"));
	public static final Item SPAWNER_SCANNER = Registry.register(BuiltInRegistries.ITEM, SPAWNER_SCANNER_KEY,
			new Item(new Item.Properties().setId(SPAWNER_SCANNER_KEY).stacksTo(1)));
	public static final ResourceKey<Item> IRON_SCANNER_KEY = ResourceKey.create(Registries.ITEM, id("iron_scanner"));
	public static final Item IRON_SCANNER = Registry.register(BuiltInRegistries.ITEM, IRON_SCANNER_KEY,
			new Item(new Item.Properties().setId(IRON_SCANNER_KEY).stacksTo(1)));

	@Override
	public void onInitialize() {
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			var player = listener.player;
			unlockAllRecipesSilently(player, server);
			// Vanilla sends the unlocked recipe book while the player joins. Queue our complete
			// display catalogue afterwards so that the vanilla packet cannot replace it again.
			server.execute(() -> sendAllRecipeDisplays(player, server));
			String starterTag = MOD_ID + ".received_starter_tools";
			if (!player.isCreative() && !player.isSpectator() && !player.entityTags().contains(starterTag)) {
				giveOrDrop(player, new ItemStack(Items.IRON_AXE));
				giveOrDrop(player, new ItemStack(Items.IRON_PICKAXE));
				player.addTag(starterTag);
			}
			String scannerTag = MOD_ID + ".received_iron_scanner";
			if (!player.isCreative() && !player.isSpectator() && !player.entityTags().contains(scannerTag)) {
				giveOrDrop(player, new ItemStack(IRON_SCANNER));
				player.addTag(scannerTag);
			}
		});
		LOGGER.info("Digi Miner Mod wurde initialisiert.");
	}

	private static void unlockAllRecipesSilently(net.minecraft.server.level.ServerPlayer player,
			net.minecraft.server.MinecraftServer server) {
		for (var recipe : server.getRecipeManager().getRecipes()) {
			player.getRecipeBook().add(recipe.id());
		}
	}

	private static void sendAllRecipeDisplays(net.minecraft.server.level.ServerPlayer player,
			net.minecraft.server.MinecraftServer server) {
		List<ClientboundRecipeBookAddPacket.Entry> entries = new ArrayList<>();
		for (var recipe : server.getRecipeManager().getRecipes()) {
			server.getRecipeManager().listDisplaysForRecipe(recipe.id(), display ->
					entries.add(new ClientboundRecipeBookAddPacket.Entry(display, false, false)));
		}
		player.connection.send(new ClientboundRecipeBookAddPacket(entries, true));
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
