package gnu.client.module.modules.player.scaffold.aim;

public final class DiagonalYawTargetPositionFactory extends BaseYawTargetPositionFactory {
  public DiagonalYawTargetPositionFactory(PositionFactoryConfiguration config) {
    super(config);
  }

  @Override
  protected float getAngle() {
    return 75.0f;
  }
}
