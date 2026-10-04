package de.sourcemaster.digiminermod.drone;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public final class ScannerCartridgeItem extends Item {
	public ScannerCartridgeItem(Properties properties) { super(properties); }

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> text, TooltipFlag flag) {
		text.accept(Component.literal("Coordinates I").withStyle(ChatFormatting.YELLOW));
		text.accept(Component.literal("Iron I").withStyle(ChatFormatting.YELLOW));
		text.accept(Component.literal("Diamond I").withStyle(ChatFormatting.AQUA));
	}

	@Override public boolean isFoil(ItemStack stack) { return true; }
}
