package gnu.client.module.modules.player.scaffold;

import gnu.client.runtime.MoveFixUtil;

/**
 * LiquidBounce {@code MovementCorrection} for Scaffold only.
 * KillAura keeps its own MoveFix path (priority 1).
 */
public final class ScaffoldMoveFix {
  public static final int OFF = 0;
  public static final int STRICT = 1;
  public static final int SILENT = 2;
  public static final int CHANGE_LOOK = 3;

  private ScaffoldMoveFix() {}

  /** Arms moveFlying/jump — same gate as KA ({@code moveFix != NONE}). */
  public static boolean armsPhysics(int mode) {
    return mode == STRICT || mode == SILENT;
  }

  /** Remaps WASD when MoveFix physics is armed (KA: any non-off MoveFix). */
  public static boolean remapsInput(int mode) {
    return mode == STRICT || mode == SILENT;
  }

  /** Writes client camera to scaffold aim. */
  public static boolean writesCamera(int mode) {
    return mode == CHANGE_LOOK;
  }

  public static int rotationPriority(int mode) {
    if (armsPhysics(mode))
      return MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY;
    return -1; // KA render-only priority
  }
}
