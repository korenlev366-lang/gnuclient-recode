package gnu.client.module.modules.player.scaffold.aim;

import net.minecraft.util.Vec3;

/** Finite segment from {@code start} to {@code end}. Port of LB {@code LineSegment}. */
public final class LineSegment3 {
  public final Vec3 start;
  public final Vec3 end;

  public LineSegment3(Vec3 start, Vec3 end) {
    if (start == null || end == null)
      throw new IllegalArgumentException("start/end null");
    if (Line3.isLikelyZero(Line3.sub(end, start)))
      throw new IllegalArgumentException("zero-length segment");
    this.start = start;
    this.end = end;
  }

  public Vec3 direction() {
    return Line3.sub(end, start);
  }

  public Vec3 pointAt(double t) {
    return new Vec3(
        start.xCoord + (end.xCoord - start.xCoord) * t,
        start.yCoord + (end.yCoord - start.yCoord) * t,
        start.zCoord + (end.zCoord - start.zCoord) * t);
  }

  public Vec3 nearestPointTo(Vec3 point) {
    Vec3 d = direction();
    double lenSq = Line3.lengthSq(d);
    if (lenSq < 1.0e-12)
      return start;
    double t = Line3.clamp01(Line3.dot(Line3.sub(point, start), d) / lenSq);
    return pointAt(t);
  }

  public Line3 asLine() {
    return new Line3(start, direction());
  }
}
