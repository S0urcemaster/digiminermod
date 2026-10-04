package de.sourcemaster.digiminermod;

import de.sourcemaster.digiminermod.drone.DroneCoreItem;
import de.sourcemaster.digiminermod.drone.DroneEntity;
import de.sourcemaster.digiminermod.drone.DroneNetworking;
import de.sourcemaster.digiminermod.drone.DroneBlock;
import de.sourcemaster.digiminermod.drone.DroneBlockEntity;
import de.sourcemaster.digiminermod.drone.DroneMode;
import de.sourcemaster.digiminermod.credit.CreditAccount;
import de.sourcemaster.digiminermod.credit.CreditTerminalBlock;
import de.sourcemaster.digiminermod.credit.CreditTerminalBlockEntity;
import de.sourcemaster.digiminermod.credit.CreditTerminalMenu;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
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
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.world.inventory.MenuType;
import de.sourcemaster.digiminermod.drone.DroneMenu;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DigiMinerMod implements ModInitializer {
	public static final String MOD_ID = "digiminermod";
	private static final String DRONE_LOCATION_TAG = MOD_ID + ".drone_location|";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final ResourceKey<Item> SPAWNER_SCANNER_KEY = ResourceKey.create(Registries.ITEM, id("spawner_scanner"));
	public static final Item SPAWNER_SCANNER = Registry.register(BuiltInRegistries.ITEM, SPAWNER_SCANNER_KEY,
			new Item(new Item.Properties().setId(SPAWNER_SCANNER_KEY).stacksTo(1)));
	public static final ResourceKey<EntityType<?>> DRONE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("drone"));
	public static final EntityType<DroneEntity> DRONE = Registry.register(BuiltInRegistries.ENTITY_TYPE, DRONE_KEY,
			EntityType.Builder.of(DroneEntity::new, MobCategory.MISC).sized(0.9F, 1.4F)
					.clientTrackingRange(10).build(DRONE_KEY));
	public static final ResourceKey<Item> DRONE_CORE_KEY = ResourceKey.create(Registries.ITEM, id("drone_core"));
	public static final Item DRONE_CORE = Registry.register(BuiltInRegistries.ITEM, DRONE_CORE_KEY,
			new DroneCoreItem(new Item.Properties().setId(DRONE_CORE_KEY).stacksTo(1)));
	public static final ResourceKey<Item> DRONE_LIGHT_KEY = ResourceKey.create(Registries.ITEM, id("drone_light"));
	public static final Item DRONE_LIGHT = Registry.register(BuiltInRegistries.ITEM, DRONE_LIGHT_KEY,
			new Item(new Item.Properties().setId(DRONE_LIGHT_KEY).stacksTo(1)));
	public static final ResourceKey<Item> DRONE_DRILL_KEY = ResourceKey.create(Registries.ITEM, id("drone_drill"));
	public static final Item DRONE_DRILL = Registry.register(BuiltInRegistries.ITEM, DRONE_DRILL_KEY,
			new Item(new Item.Properties().setId(DRONE_DRILL_KEY).stacksTo(1)));
	public static final ResourceKey<Item> DRONE_BUILDER_KEY = ResourceKey.create(Registries.ITEM, id("drone_builder"));
	public static final Item DRONE_BUILDER = Registry.register(BuiltInRegistries.ITEM, DRONE_BUILDER_KEY,
			new Item(new Item.Properties().setId(DRONE_BUILDER_KEY).stacksTo(1)));
	public static final ResourceKey<Item> BASIC_PROGRAM_DRIVE_KEY = ResourceKey.create(Registries.ITEM, id("basic_program_drive"));
	public static final Item BASIC_PROGRAM_DRIVE = Registry.register(BuiltInRegistries.ITEM, BASIC_PROGRAM_DRIVE_KEY,
			new Item(new Item.Properties().setId(BASIC_PROGRAM_DRIVE_KEY).stacksTo(1)));
	public static final ResourceKey<Item> BASIC_BUILD_CARTRIDGE_KEY = ResourceKey.create(Registries.ITEM, id("basic_build_cartridge"));
	public static final Item BASIC_BUILD_CARTRIDGE = Registry.register(BuiltInRegistries.ITEM, BASIC_BUILD_CARTRIDGE_KEY,
			new Item(new Item.Properties().setId(BASIC_BUILD_CARTRIDGE_KEY).stacksTo(1)));
	public static final ResourceKey<Item> BASIC_EXCAVATE_CARTRIDGE_KEY = ResourceKey.create(Registries.ITEM, id("basic_excavate_cartridge"));
	public static final Item BASIC_EXCAVATE_CARTRIDGE = Registry.register(BuiltInRegistries.ITEM, BASIC_EXCAVATE_CARTRIDGE_KEY,
			new Item(new Item.Properties().setId(BASIC_EXCAVATE_CARTRIDGE_KEY).stacksTo(1)));
	public static final ResourceKey<Item> BASIC_SCANNER_CARTRIDGE_KEY = ResourceKey.create(Registries.ITEM, id("basic_scanner_cartridge"));
	public static final Item BASIC_SCANNER_CARTRIDGE = Registry.register(BuiltInRegistries.ITEM, BASIC_SCANNER_CARTRIDGE_KEY,
			new Item(new Item.Properties().setId(BASIC_SCANNER_CARTRIDGE_KEY).stacksTo(1)));
	public static final ResourceKey<Block> DRONE_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, id("drone"));
	public static final DroneBlock DRONE_BLOCK = Registry.register(BuiltInRegistries.BLOCK, DRONE_BLOCK_KEY,
			new DroneBlock(BlockBehaviour.Properties.of().setId(DRONE_BLOCK_KEY).strength(-1.0F, 3600000.0F)
					.sound(SoundType.GLASS).lightLevel(state -> state.getValue(DroneBlock.LIT) ? 12 : 0).noOcclusion().noLootTable()));
	public static final ResourceKey<BlockEntityType<?>> DRONE_BLOCK_ENTITY_KEY = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, id("drone"));
	public static final BlockEntityType<DroneBlockEntity> DRONE_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, DRONE_BLOCK_ENTITY_KEY,
			new BlockEntityType<>(DroneBlockEntity::new, Set.of(DRONE_BLOCK)));
	public static final ResourceKey<MenuType<?>> DRONE_MENU_KEY = ResourceKey.create(Registries.MENU, id("drone"));
	public static final MenuType<DroneMenu> DRONE_MENU = Registry.register(BuiltInRegistries.MENU, DRONE_MENU_KEY,
			new ExtendedMenuType<>(DroneMenu::new, net.minecraft.core.BlockPos.STREAM_CODEC.cast()));
	public static final ResourceKey<Block> CREDIT_TERMINAL_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, id("credit_terminal"));
	public static final CreditTerminalBlock CREDIT_TERMINAL_BLOCK = Registry.register(BuiltInRegistries.BLOCK, CREDIT_TERMINAL_BLOCK_KEY,
			new CreditTerminalBlock(BlockBehaviour.Properties.of().setId(CREDIT_TERMINAL_BLOCK_KEY).strength(4.0F)
					.sound(SoundType.METAL).requiresCorrectToolForDrops()));
	public static final ResourceKey<Item> CREDIT_TERMINAL_ITEM_KEY = ResourceKey.create(Registries.ITEM, id("credit_terminal"));
	public static final Item CREDIT_TERMINAL_ITEM = Registry.register(BuiltInRegistries.ITEM, CREDIT_TERMINAL_ITEM_KEY,
			new BlockItem(CREDIT_TERMINAL_BLOCK, new Item.Properties().setId(CREDIT_TERMINAL_ITEM_KEY)));
	public static final ResourceKey<BlockEntityType<?>> CREDIT_TERMINAL_BLOCK_ENTITY_KEY = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, id("credit_terminal"));
	public static final BlockEntityType<CreditTerminalBlockEntity> CREDIT_TERMINAL_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, CREDIT_TERMINAL_BLOCK_ENTITY_KEY,
			new BlockEntityType<>(CreditTerminalBlockEntity::new, Set.of(CREDIT_TERMINAL_BLOCK)));
	public static final ResourceKey<MenuType<?>> CREDIT_TERMINAL_MENU_KEY = ResourceKey.create(Registries.MENU, id("credit_terminal"));
	public static final MenuType<CreditTerminalMenu> CREDIT_TERMINAL_MENU = Registry.register(BuiltInRegistries.MENU, CREDIT_TERMINAL_MENU_KEY,
			new ExtendedMenuType<>(CreditTerminalMenu::new, net.minecraft.core.BlockPos.STREAM_CODEC.cast()));

	@Override
	public void onInitialize() {
		FabricDefaultAttributeRegistry.register(DRONE, DroneEntity.createAttributes());
		DroneNetworking.registerServer();
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (hand != net.minecraft.world.InteractionHand.MAIN_HAND || !level.getBlockState(pos).is(DRONE_BLOCK)) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DroneBlockEntity drone
					&& drone.isOwner(player) && drone.getMode() == DroneMode.STATIC) {
				drone.pushFromHit(direction);
			}
			return net.minecraft.world.InteractionResult.SUCCESS;
		});
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			var player = listener.player;
			removeLegacyDroneCores(player);
			server.execute(() -> ensureDrone(player));
			server.execute(() -> ensureCreditTerminal(player));
			server.execute(() -> CreditAccount.sync(player));
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
		});
		LOGGER.info("Digi Miner Mod wurde initialisiert.");
	}

	private static void ensureCreditTerminal(net.minecraft.server.level.ServerPlayer owner) {
		String tag = MOD_ID + ".received_credit_terminal";
		if (owner.entityTags().contains(tag) || !(owner.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
		int x = owner.blockPosition().getX() + 3;
		int z = owner.blockPosition().getZ();
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		var pos = new net.minecraft.core.BlockPos(x, y, z);
		for (int i = 0; i < 6 && !level.getBlockState(pos).canBeReplaced(); i++) pos = pos.above();
		level.setBlock(pos, CREDIT_TERMINAL_BLOCK.defaultBlockState()
				.setValue(CreditTerminalBlock.FACING, net.minecraft.core.Direction.WEST), 3);
		if (level.getBlockEntity(pos) instanceof CreditTerminalBlockEntity terminal) terminal.setOwner(owner.getUUID());
		owner.addTag(tag);
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
		DroneBlockEntity remembered = findRememberedDrone(owner);
		DroneBlockEntity found = remembered;
		for (var pos : net.minecraft.core.BlockPos.betweenClosed(owner.blockPosition().offset(-32, -16, -32),
				owner.blockPosition().offset(32, 16, 32))) {
			if (!owner.level().getBlockState(pos).is(DRONE_BLOCK)) continue;
			if (owner.level().getBlockEntity(pos) instanceof DroneBlockEntity drone && drone.isOwner(owner)) {
				if (found == null) found = drone;
				else if (found != drone) owner.level().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
			} else {
				owner.level().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
			}
		}
		if (found != null) { rememberDroneLocation(owner, found.getLevel(), found.getBlockPos()); return; }
		var pos = owner.blockPosition().relative(owner.getDirection().getOpposite(), 3).above();
		if (!owner.level().getBlockState(pos).canBeReplaced()) pos = owner.blockPosition().above(2);
		owner.level().setBlock(pos, DRONE_BLOCK.defaultBlockState(), 3);
		if (owner.level().getBlockEntity(pos) instanceof DroneBlockEntity drone) drone.restore(owner.getUUID(), DroneMode.FOLLOW, java.util.List.of());
	}

	private static DroneBlockEntity findRememberedDrone(net.minecraft.server.level.ServerPlayer owner) {
		for (String tag : owner.entityTags()) {
			if (!tag.startsWith(DRONE_LOCATION_TAG)) continue;
			try {
				String[] parts = tag.substring(DRONE_LOCATION_TAG.length()).split("\\|");
				if (parts.length != 4) continue;
				var dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(parts[0]));
				var level = owner.level().getServer().getLevel(dimension);
				if (level == null) continue;
				var pos = new net.minecraft.core.BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
				level.getChunkAt(pos);
				if (level.getBlockEntity(pos) instanceof DroneBlockEntity drone && drone.isOwner(owner)) return drone;
				if (level.getBlockState(pos).is(DRONE_BLOCK)) level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
			} catch (RuntimeException ignored) {
				// A malformed legacy location is replaced below.
			}
		}
		return null;
	}

	public static void rememberDroneLocation(net.minecraft.server.level.ServerPlayer owner,
			net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
		owner.entityTags().removeIf(tag -> tag.startsWith(DRONE_LOCATION_TAG));
		owner.addTag(DRONE_LOCATION_TAG + level.dimension().identifier() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ());
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
