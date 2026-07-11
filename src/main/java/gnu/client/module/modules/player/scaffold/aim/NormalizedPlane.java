package gnu.client.module.modules.player.scaffold.aim;

import net.minecraft.util.Vec3;

/** Port of LB {@code NormalizedPlane}. */
public final class NormalizedPlane {
  private static final double EPS = 1.0e-9;

  public final Vec3 pos;
  public final Vec3 normal;

  public NormalizedPlane(Vec3 pos, Vec3 normal) {
    this.pos = pos;
    this.normal = Line3.normalize(normal);
  }

  public static NormalizedPlane fromParams(Vec3 base, Vec3 directionA, Vec3 directionB) {
    Vec3 n = Line3.cross(directionA, directionB);
    if (Line3.isLikelyZero(n))
      throw new IllegalArgumentException("degenerate plane");
    return new NormalizedPlane(base, n);
  }

  public static NormalizedPlane fromPoints(Vec3 a, Vec3 b, Vec3 c) {
    return fromParams(a, Line3.sub(b, a), Line3.sub(c, a));
  }

  public Double intersectionPhi(Line3 line) {
    double e = Line3.dot(line.direction, normal);
    if (Math.abs(e) < EPS)
      return null;
    double d = Line3.dot(pos, normal);
    return (d - Line3.dot(line.origin, normal)) / e;
  }

  public Vec3 intersection(Line3 line) {
    Double phi = intersectionPhi(line);
    if (phi == null || !Double.isFinite(phi))
      return null;
    return line.pointAt(phi);
  }

  /** Plane–plane intersection as an infinite line, or null if parallel. */
  public Line3 intersection(NormalizedPlane other) {
    Vec3 direction = Line3.cross(other.normal, normal);
    double dirLenSq = Line3.lengthSq(direction);
    if (dirLenSq < EPS)
      return null;
    double firstDistance = Line3.dot(other.normal, other.pos);
    double secondDistance = Line3.dot(normal, pos);
    Vec3 point = Line3.add(
        Line3.scale(Line3.cross(normal, direction), firstDistance),
        Line3.scale(Line3.cross(direction, other.normal), secondDistance));
    point = Line3.scale(point, 1.0 / dirLenSq);
    return new Line3(point, direction);
  }
}
