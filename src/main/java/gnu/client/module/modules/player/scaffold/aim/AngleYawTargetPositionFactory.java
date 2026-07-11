package gnu.client.module.modules.player.scaffold.aim;

public final class AngleYawTargetPositionFactory extends BaseYawTargetPositionFactory {
  public AngleYawTargetPositionFactory(PositionFactoryConfiguration config) {
    super(config);
  }

  @Override
  protected float getAngle() {
    return 45.0f;
  }
}
