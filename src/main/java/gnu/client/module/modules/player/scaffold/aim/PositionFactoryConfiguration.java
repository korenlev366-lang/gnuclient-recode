package gnu.client.module.modules.player.scaffold.aim;

import net.minecraft.util.Vec3;

/** LB {@code PositionFactoryConfiguration}. */
public final class PositionFactoryConfiguration {
  public final Vec3 eyePos;
  /** Random in [-1, 1] (may be constant). */
  public final double randomNumber;

  public PositionFactoryConfiguration(Vec3 eyePos, double randomNumber) {
    this.eyePos = eyePos;
    this.randomNumber = randomNumber;
  }
}
