package de.sourcemaster.digiminermod;

import de.sourcemaster.digiminermod.drone.DroneCoreItem;
import de.sourcemaster.digiminermod.drone.DroneEntity;
import de.sourcemaster.digiminermod.drone.DroneNetworking;
import de.sourcemaster.digiminermod.drone.DroneBlock;
import de.sourcemaster.digiminermod.drone.DroneBlockEntity;
import de.sourcemaster.digiminermod.drone.DroneMode;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.Set;
import java.util.UUID;
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
	public static final ResourceKey<EntityType<?>> DRONE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("drone"));
	public static final EntityType<DroneEntity> DRONE = Registry.register(BuiltInRegistries.ENTITY_TYPE, DRONE_KEY,
			EntityType.Builder.of(DroneEntity::new, MobCategory.MISC).sized(0.9F, 1.4F)
					.clientTrackingRange(10).build(DRONE_KEY));
	public static final ResourceKey<Item> DRONE_CORE_KEY = ResourceKey.create(Registries.ITEM, id("drone_core"));
	public static final Item DRONE_CORE = Registry.register(BuiltInRegistries.ITEM, DRONE_CORE_KEY,
			new DroneCoreItem(new Item.Properties().setId(DRONE_CORE_KEY).stacksTo(1)));
	public static final ResourceKey<Block> DRONE_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, id("drone"));
	public static final DroneBlock DRONE_BLOCK = Registry.register(BuiltInRegistries.BLOCK, DRONE_BLOCK_KEY,
			new DroneBlock(BlockBehaviour.Properties.of().setId(DRONE_BLOCK_KEY).strength(1.5F)
					.sound(SoundType.GLASS).lightLevel(state -> 10).noOcclusion().noLootTable()));
	public static final ResourceKey<BlockEntityType<?>> DRONE_BLOCK_ENTITY_KEY = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, id("drone"));
	public static final BlockEntityType<DroneBlockEntity> DRONE_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, DRONE_BLOCK_ENTITY_KEY,
			new BlockEntityType<>(DroneBlockEntity::new, Set.of(DRONE_BLOCK)));

	@Override
	public void onInitialize() {
		FabricDefaultAttributeRegistry.register(DRONE, DroneEntity.createAttributes());
		DroneNetworking.registerServer();
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			var player = listener.player;
			removeLegacyDroneCores(player);
			server.execute(() -> ensureDrone(player));
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

	public static DroneEntity findDrone(net.minecraft.server.level.ServerPlayer owner) {
		for (var level : owner.level().getServer().getAllLevels()) {
			for (var entity : level.getAllEntities()) {
				if (entity instanceof DroneEntity drone && drone.isOwner(owner)) return drone;
			}
		}
		return null;
	}

	private static void ensureDrone(net.minecraft.server.level.ServerPlayer owner) {
		if (owner.isSpectator()) return;
		for (var level : owner.level().getServer().getAllLevels()) {
			for (var entity : level.getAllEntities()) if (entity instanceof DroneEntity drone) drone.discard();
		}
		for (var pos : net.minecraft.core.BlockPos.betweenClosed(owner.blockPosition().offset(-32, -16, -32),
				owner.blockPosition().offset(32, 16, 32))) {
			if (owner.level().getBlockEntity(pos) instanceof DroneBlockEntity drone && drone.isOwner(owner)) return;
		}
		var pos = owner.blockPosition().relative(owner.getDirection().getOpposite(), 3).above();
		if (!owner.level().getBlockState(pos).canBeReplaced()) pos = owner.blockPosition().above(2);
		owner.level().setBlock(pos, DRONE_BLOCK.defaultBlockState(), 3);
		if (owner.level().getBlockEntity(pos) instanceof DroneBlockEntity drone) drone.restore(owner.getUUID(), DroneMode.FOLLOW, java.util.List.of());
	}

	public static void respawnDroneAtSpawn(net.minecraft.server.level.ServerLevel level, UUID owner, DroneMode mode,
			java.util.List<ItemStack> stacks) {
		var respawn = level.getServer().getRespawnData();
		var spawnLevel = level.getServer().getLevel(respawn.dimension());
		if (spawnLevel == null) spawnLevel = level;
		var pos = respawn.pos().above();
		for (int y = 0; y < 8 && !spawnLevel.getBlockState(pos).canBeReplaced(); y++) pos = pos.above();
		spawnLevel.setBlock(pos, DRONE_BLOCK.defaultBlockState(), 3);
		if (spawnLevel.getBlockEntity(pos) instanceof DroneBlockEntity drone) drone.restore(owner, mode, stacks);
	}

	private static void removeLegacyDroneCores(net.minecraft.server.level.ServerPlayer player) {
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).is(DRONE_CORE)) inventory.setItem(slot, ItemStack.EMPTY);
		}
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
