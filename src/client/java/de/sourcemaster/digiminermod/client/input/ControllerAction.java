package de.sourcemaster.digiminermod.client.input;

public enum ControllerAction {
	WORLD_JUMP("World: Jump", ControllerButton.A),
	WORLD_ATTACK("World: Attack / Destroy", ControllerButton.RT),
	WORLD_USE("World: Use / Place", ControllerButton.LT),
	WORLD_SNEAK("World: Sneak", ControllerButton.B),
	WORLD_SPRINT("World: Sprint", ControllerButton.Y),
	WORLD_CHANGE_PERSPECTIVE("World: Change perspective", ControllerButton.DPAD_UP),
	OPEN_INVENTORY("World: Open inventory", ControllerButton.VIEW),
	INVENTORY_TRANSFER_HOTBAR("Inventory: Transfer hotbar", ControllerButton.DPAD_DOWN),
	INVENTORY_TRANSFER_SECONDARY("Inventory: Transfer secondary", ControllerButton.A),
	INVENTORY_HOTBAR_PREVIOUS("Inventory: Hotbar previous", ControllerButton.LB),
	INVENTORY_HOTBAR_NEXT("Inventory: Hotbar next", ControllerButton.RB),
	INVENTORY_PAGE_PREVIOUS("Inventory: Page previous", ControllerButton.LT),
	INVENTORY_PAGE_NEXT("Inventory: Page next", ControllerButton.RT),
	INVENTORY_CLOSE("Inventory: Close", ControllerButton.VIEW);

	private final String label;
	private final ControllerButton defaultButton;

	ControllerAction(String label, ControllerButton defaultButton) {
		this.label = label;
		this.defaultButton = defaultButton;
	}

	public String label() { return this.label; }
	public ControllerButton defaultButton() { return this.defaultButton; }
}
