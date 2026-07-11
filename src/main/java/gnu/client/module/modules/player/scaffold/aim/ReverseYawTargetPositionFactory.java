package gnu.client.module.modules.player.scaffold.aim;

public final class ReverseYawTargetPositionFactory extends BaseYawTargetPositionFactory {
  public ReverseYawTargetPositionFactory(PositionFactoryConfiguration config) {
    super(config);
  }

  @Override
  protected float getAngle() {
    return 180.0f;
  }
}
