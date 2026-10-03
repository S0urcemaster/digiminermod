package de.sourcemaster.digiminermod.client.input;

public enum ControllerButton {
	A("A"), B("B"), X("X"), Y("Y"),
	LB("LB"), RB("RB"), LT("LT"), RT("RT"),
	VIEW("View"), MENU("Menu"),
	DPAD_UP("D-pad Up"), DPAD_DOWN("D-pad Down"),
	DPAD_LEFT("D-pad Left"), DPAD_RIGHT("D-pad Right");

	private final String label;

	ControllerButton(String label) { this.label = label; }
	public String label() { return this.label; }
}
