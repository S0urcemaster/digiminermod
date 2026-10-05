package de.sourcemaster.digiminermod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class InventoryNetworking {
	private InventoryNetworking() {}

	public record TransferOnePayload(int containerId, int sourceSlot, int targetSlot)
			implements CustomPacketPayload {
		public static final Type<TransferOnePayload> TYPE = new Type<>(DigiMinerMod.id("transfer_one"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TransferOnePayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, TransferOnePayload::containerId,
				ByteBufCodecs.VAR_INT, TransferOnePayload::sourceSlot,
				ByteBufCodecs.VAR_INT, TransferOnePayload::targetSlot,
				TransferOnePayload::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record SortPayload(int containerId, int firstSlot, int slotCount, boolean descending)
			implements CustomPacketPayload {
		public static final Type<SortPayload> TYPE = new Type<>(DigiMinerMod.id("sort_container"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SortPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, SortPayload::containerId,
				ByteBufCodecs.VAR_INT, SortPayload::firstSlot,
				ByteBufCodecs.VAR_INT, SortPayload::slotCount,
				ByteBufCodecs.BOOL, SortPayload::descending,
				SortPayload::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public static void registerServer() {
		PayloadTypeRegistry.serverboundPlay().register(TransferOnePayload.TYPE, TransferOnePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SortPayload.TYPE, SortPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TransferOnePayload.TYPE, (payload, context) -> {
			var player = context.player();
			var menu = player.containerMenu;
			if (menu.containerId != payload.containerId() || payload.sourceSlot() == payload.targetSlot()
					|| payload.sourceSlot() < 0 || payload.targetSlot() < 0
					|| payload.sourceSlot() >= menu.slots.size() || payload.targetSlot() >= menu.slots.size()) return;
			var source = menu.getSlot(payload.sourceSlot());
			var target = menu.getSlot(payload.targetSlot());
			ItemStack sourceStack = source.getItem();
			ItemStack targetStack = target.getItem();
			if (sourceStack.isEmpty() || !target.mayPlace(sourceStack)) return;
			if (!targetStack.isEmpty() && !ItemStack.isSameItemSameComponents(sourceStack, targetStack)) return;
			int limit = Math.min(sourceStack.getMaxStackSize(), target.getMaxStackSize(sourceStack));
			if (targetStack.getCount() >= limit) return;
			ItemStack taken = source.safeTake(1, 1, player);
			if (taken.isEmpty()) return;
			ItemStack remainder = target.safeInsert(taken, 1);
			if (!remainder.isEmpty()) player.drop(remainder, false);
			menu.broadcastChanges();
		});
		ServerPlayNetworking.registerGlobalReceiver(SortPayload.TYPE, (payload, context) -> {
			var player = context.player();
			var menu = player.containerMenu;
			int first = payload.firstSlot();
			int count = payload.slotCount();
			if (menu.containerId != payload.containerId() || first < 0 || count < 2
					|| count > 54 || first + count > menu.slots.size()) return;
			List<ItemStack> merged = new ArrayList<>();
			for (int i = first; i < first + count; i++) {
				var slot = menu.getSlot(i);
				ItemStack original = slot.getItem();
				if (!original.isEmpty() && (!slot.mayPickup(player) || !slot.mayPlace(original))) return;
				ItemStack remaining = original.copy();
				for (ItemStack stack : merged) {
					if (!remaining.isEmpty() && ItemStack.isSameItemSameComponents(stack, remaining)
							&& stack.getCount() < stack.getMaxStackSize()) {
						int moved = Math.min(remaining.getCount(), stack.getMaxStackSize() - stack.getCount());
						stack.grow(moved);
						remaining.shrink(moved);
					}
				}
				while (!remaining.isEmpty()) {
					int amount = Math.min(remaining.getCount(), remaining.getMaxStackSize());
					merged.add(remaining.copyWithCount(amount));
					remaining.shrink(amount);
				}
			}
			Comparator<ItemStack> comparator = (left, right) -> {
				boolean leftSingle = left.getMaxStackSize() == 1;
				boolean rightSingle = right.getMaxStackSize() == 1;
				if (leftSingle != rightSingle) return payload.descending()
						? Boolean.compare(leftSingle, rightSingle) : Boolean.compare(rightSingle, leftSingle);
				int size = payload.descending()
						? Integer.compare(right.getCount(), left.getCount())
						: Integer.compare(left.getCount(), right.getCount());
				return size != 0 ? size : left.getHoverName().getString()
						.compareToIgnoreCase(right.getHoverName().getString());
			};
			merged.sort(comparator);
			for (int offset = 0; offset < count; offset++) {
				ItemStack stack = offset < merged.size() ? merged.get(offset) : ItemStack.EMPTY;
				if (!stack.isEmpty() && !menu.getSlot(first + offset).mayPlace(stack)) return;
			}
			for (int offset = 0; offset < count; offset++) {
				menu.getSlot(first + offset).set(offset < merged.size() ? merged.get(offset) : ItemStack.EMPTY);
			}
			menu.broadcastChanges();
		});
	}
}
