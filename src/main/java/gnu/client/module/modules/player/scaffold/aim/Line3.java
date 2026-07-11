package gnu.client.module.modules.player.scaffold.aim;

import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/**
 * Infinite line: {@code origin + direction * t}. Port of LB {@code Line} / {@code LinearGeometry3}.
 */
public final class Line3 {
  private static final double EPS = 1.0e-9;

  public final Vec3 origin;
  public final Vec3 direction;

  public Line3(Vec3 origin, Vec3 direction) {
    if (origin == null || direction == null)
      throw new IllegalArgumentException("origin/direction null");
    if (isLikelyZero(direction))
      throw new IllegalArgumentException("direction must be non-zero");
    this.origin = origin;
    this.direction = direction;
  }

  public static Line3 fromPoints(Vec3 begin, Vec3 end) {
    return new Line3(begin, sub(end, begin));
  }

  /** Look direction from yaw/pitch degrees (MC 1.8 convention). */
  public static Vec3 directionVector(float yawDeg, float pitchDeg) {
    float yawRad = (float) Math.toRadians(yawDeg);
    float pitchRad = (float) Math.toRadians(pitchDeg);
    float cosPitch = MathHelper.cos(pitchRad);
    return new Vec3(
        -MathHelper.sin(yawRad) * cosPitch,
        -MathHelper.sin(pitchRad),
        MathHelper.cos(yawRad) * cosPitch);
  }

  /** Horizontal unit direction from yaw degrees (pitch 0). */
  public static Vec3 directionFromYaw(float yawDeg) {
    float yawRad = (float) Math.toRadians(yawDeg);
    return new Vec3(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
  }

  /** LB {@code Vec3.Z_AXIS.yRot(yawRad)} — used by yaw-plane factories. */
  public static Vec3 zAxisYRot(float yawDeg) {
    float yawRad = (float) Math.toRadians(yawDeg);
    float c = MathHelper.cos(yawRad);
    float s = MathHelper.sin(yawRad);
    return new Vec3(s, 0.0, c);
  }

  public Vec3 pointAt(double t) {
    return new Vec3(
        origin.xCoord + direction.xCoord * t,
        origin.yCoord + direction.yCoord * t,
        origin.zCoord + direction.zCoord * t);
  }

  public double parameterFor(Vec3 point) {
    double lenSq = lengthSq(direction);
    if (lenSq < EPS)
      return 0.0;
    return dot(sub(point, origin), direction) / lenSq;
  }

  public Vec3 nearestPointTo(Vec3 point) {
    return pointAt(parameterFor(point));
  }

  public double distanceToSqr(Vec3 point) {
    Vec3 n = nearestPointTo(point);
    return distSq(n, point);
  }

  /**
   * Closest points between this infinite line and {@code other} (infinite or segment).
   * @return {@code [pointOnThis, pointOnOther]} or null
   */
  public Vec3[] nearestPointsTo(Line3 other) {
    return nearestPointsTo(other, false);
  }

  public Vec3[] nearestPointsTo(LineSegment3 segment) {
    Line3 other = new Line3(segment.start, sub(segment.end, segment.start));
    return nearestPointsTo(other, true);
  }

  private Vec3[] nearestPointsTo(Line3 other, boolean clampOtherTo01) {
    Vec3 d1 = direction;
    Vec3 d2 = other.direction;
    double a = dot(d1, d1);
    double b = dot(d1, d2);
    double c = dot(d2, d2);
    Vec3 w0 = sub(origin, other.origin);
    double d = dot(d1, w0);
    double e = dot(d2, w0);
    double det = a * c - b * b;

    double s;
    double t;
    if (Math.abs(det) > EPS) {
      s = (b * e - c * d) / det;
      t = (a * e - b * d) / det;
    } else {
      s = 0.0;
      t = (c > EPS) ? e / c : 0.0;
    }
    if (clampOtherTo01)
      t = clamp01(t);

    // Also evaluate endpoints when other is a segment
    if (clampOtherTo01) {
      double best = Double.POSITIVE_INFINITY;
      double bestS = s;
      double bestT = t;
      double[] tCandidates = {t, 0.0, 1.0};
      for (double tc : tCandidates) {
        tc = clamp01(tc);
        double sc = (a > EPS) ? (b * tc - d) / a : 0.0;
        Vec3 p1 = pointAt(sc);
        Vec3 p2 = other.pointAt(tc);
        double dist = distSq(p1, p2);
        if (dist < best) {
          best = dist;
          bestS = sc;
          bestT = tc;
        }
      }
      s = bestS;
      t = bestT;
    }

    return new Vec3[] {pointAt(s), other.pointAt(t)};
  }

  static boolean isLikelyZero(Vec3 v) {
    return lengthSq(v) < 1.0e-10;
  }

  static double lengthSq(Vec3 v) {
    return v.xCoord * v.xCoord + v.yCoord * v.yCoord + v.zCoord * v.zCoord;
  }

  static double dot(Vec3 a, Vec3 b) {
    return a.xCoord * b.xCoord + a.yCoord * b.yCoord + a.zCoord * b.zCoord;
  }

  static Vec3 sub(Vec3 a, Vec3 b) {
    return new Vec3(a.xCoord - b.xCoord, a.yCoord - b.yCoord, a.zCoord - b.zCoord);
  }

  static Vec3 add(Vec3 a, Vec3 b) {
    return new Vec3(a.xCoord + b.xCoord, a.yCoord + b.yCoord, a.zCoord + b.zCoord);
  }

  static Vec3 scale(Vec3 a, double s) {
    return new Vec3(a.xCoord * s, a.yCoord * s, a.zCoord * s);
  }

  static Vec3 cross(Vec3 a, Vec3 b) {
    return new Vec3(
        a.yCoord * b.zCoord - a.zCoord * b.yCoord,
        a.zCoord * b.xCoord - a.xCoord * b.zCoord,
        a.xCoord * b.yCoord - a.yCoord * b.xCoord);
  }

  static double distSq(Vec3 a, Vec3 b) {
    return lengthSq(sub(a, b));
  }

  static Vec3 normalize(Vec3 v) {
    double len = Math.sqrt(lengthSq(v));
    if (len < EPS)
      return new Vec3(0, 0, 0);
    return scale(v, 1.0 / len);
  }

  static double clamp01(double v) {
    if (v < 0.0)
      return 0.0;
    if (v > 1.0)
      return 1.0;
    return v;
  }

  /** Yaw degrees of a direction vector (LB {@code Vec3.yaw}). */
  public static float yawOf(Vec3 v) {
    return (float) Math.toDegrees(Math.atan2(-v.xCoord, v.zCoord));
  }
}
