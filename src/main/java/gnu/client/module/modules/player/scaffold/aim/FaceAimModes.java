package gnu.client.module.modules.player.scaffold.aim;

import gnu.client.module.modules.player.scaffold.ScaffoldTargetFinding;

/**
 * Resolves aim-mode int → {@link FaceTargetPositionFactory} (LB {@code getFacePositionFactoryForConfig}).
 */
public final class FaceAimModes {
  private FaceAimModes() {}

  public static FaceTargetPositionFactory resolve(int aimModeInt, PositionFactoryConfiguration config,
                                                  Line3 optimalLineOrNull) {
    switch (aimModeInt) {
      case ScaffoldTargetFinding.AIM_RANDOM:
        return RandomTargetPositionFactory.INSTANCE;
      case ScaffoldTargetFinding.AIM_STABILIZED:
        return new StabilizedRotationTargetPositionFactory(config, optimalLineOrNull);
      case ScaffoldTargetFinding.AIM_NEAREST:
        return new NearestRotationTargetPositionFactory(config);
      case ScaffoldTargetFinding.AIM_REVERSE_YAW:
        return new ReverseYawTargetPositionFactory(config);
      case ScaffoldTargetFinding.AIM_DIAGONAL_YAW:
        return new DiagonalYawTargetPositionFactory(config);
      case ScaffoldTargetFinding.AIM_ANGLE_YAW:
        return new AngleYawTargetPositionFactory(config);
      case ScaffoldTargetFinding.AIM_EDGE:
        return new EdgePointTargetPositionFactory(config);
      case ScaffoldTargetFinding.AIM_CENTER:
      default:
        return CenterTargetPositionFactory.INSTANCE;
    }
  }
}
