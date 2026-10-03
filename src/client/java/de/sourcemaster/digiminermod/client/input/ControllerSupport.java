package de.sourcemaster.digiminermod.client.input;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public final class ControllerSupport {
	private ControllerSupport() {
	}

	public static float applyDeadzone(float value, float deadzone) {
		float magnitude = Math.abs(value);
		if (magnitude <= deadzone) {
			return 0.0F;
		}
		return Math.copySign((magnitude - deadzone) / (1.0F - deadzone), value);
	}

	public static Snapshot poll() {
		for (int joystick = GLFW.GLFW_JOYSTICK_1; joystick <= GLFW.GLFW_JOYSTICK_LAST; joystick++) {
			if (GLFW.glfwJoystickIsGamepad(joystick)) {
				return pollMapped(joystick);
			}
		}

		for (int joystick = GLFW.GLFW_JOYSTICK_1; joystick <= GLFW.GLFW_JOYSTICK_LAST; joystick++) {
			if (!GLFW.glfwJoystickPresent(joystick)) {
				continue;
			}

			String name = GLFW.glfwGetJoystickName(joystick);
			String normalizedName = name == null ? "" : name.toLowerCase();
			FloatBuffer axes = GLFW.glfwGetJoystickAxes(joystick);
			if (axes != null && axes.remaining() >= 6
					&& (normalizedName.contains("x-box") || normalizedName.contains("xbox")
					|| normalizedName.contains("controller") || normalizedName.contains("gamepad"))) {
				return pollRawXbox(joystick, name);
			}
		}

		return Snapshot.NONE;
	}

	private static Snapshot pollMapped(int joystick) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			GLFWGamepadState state = GLFWGamepadState.malloc(stack);
			if (!GLFW.glfwGetGamepadState(joystick, state)) {
				return Snapshot.NONE;
			}

			String name = GLFW.glfwGetGamepadName(joystick);
			return new Snapshot(true, name == null ? "Controller" : name, true,
					state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X),
					state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y),
					state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X),
					state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y),
					state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER),
					state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER),
					pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_A),
					pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_B),
					pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_X),
					pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_Y),
					pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER),
					pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER),
					pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_BACK),
					pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_START),
					pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP),
					pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN),
					pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT),
					pressed(state, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT));
		}
	}

	private static Snapshot pollRawXbox(int joystick, String name) {
		FloatBuffer axes = GLFW.glfwGetJoystickAxes(joystick);
		ByteBuffer buttons = GLFW.glfwGetJoystickButtons(joystick);
		ByteBuffer hats = GLFW.glfwGetJoystickHats(joystick);
		if (axes == null || axes.remaining() < 6 || buttons == null || buttons.remaining() < 7) {
			return Snapshot.NONE;
		}

		int hat = hats != null && hats.remaining() > 0 ? hats.get(0) : 0;
		boolean dpadDown = (hat & GLFW.GLFW_HAT_DOWN) != 0;
		if (!dpadDown && buttons.remaining() > 13) {
			dpadDown = buttons.get(13) == GLFW.GLFW_PRESS;
		}

		return new Snapshot(true, name == null ? "Xbox Controller" : name, false,
				axes.get(0), axes.get(1), axes.get(3), axes.get(4), axes.get(2), axes.get(5),
				buttons.get(0) == GLFW.GLFW_PRESS,
				buttons.get(1) == GLFW.GLFW_PRESS,
				buttons.get(2) == GLFW.GLFW_PRESS,
				buttons.get(3) == GLFW.GLFW_PRESS,
				buttons.get(4) == GLFW.GLFW_PRESS,
				buttons.get(5) == GLFW.GLFW_PRESS,
				buttons.get(6) == GLFW.GLFW_PRESS,
				buttons.remaining() > 7 && buttons.get(7) == GLFW.GLFW_PRESS,
				(hat & GLFW.GLFW_HAT_UP) != 0,
				dpadDown,
				(hat & GLFW.GLFW_HAT_LEFT) != 0,
				(hat & GLFW.GLFW_HAT_RIGHT) != 0);
	}

	private static boolean pressed(GLFWGamepadState state, int button) {
		return state.buttons(button) == GLFW.GLFW_PRESS;
	}

	public record Snapshot(
			boolean connected,
			String name,
			boolean mapped,
			float leftX,
			float leftY,
			float rightX,
			float rightY,
			float leftTrigger,
			float rightTrigger,
			boolean a,
			boolean b,
			boolean x,
			boolean y,
			boolean leftBumper,
			boolean rightBumper,
			boolean menuLeft,
			boolean menuRight,
			boolean dpadUp,
			boolean dpadDown,
			boolean dpadLeft,
			boolean dpadRight) {
		public static final Snapshot NONE = new Snapshot(
				false, "", false, 0, 0, 0, 0, -1, -1,
				false, false, false, false, false, false, false, false,
				false, false, false, false);

		public boolean pressed(ControllerButton button) {
			return switch (button) {
				case A -> this.a;
				case B -> this.b;
				case X -> this.x;
				case Y -> this.y;
				case LB -> this.leftBumper;
				case RB -> this.rightBumper;
				case LT -> this.leftTrigger > 0.5F;
				case RT -> this.rightTrigger > 0.5F;
				case VIEW -> this.menuLeft;
				case MENU -> this.menuRight;
				case DPAD_UP -> this.dpadUp;
				case DPAD_DOWN -> this.dpadDown;
				case DPAD_LEFT -> this.dpadLeft;
				case DPAD_RIGHT -> this.dpadRight;
			};
		}
	}
}
