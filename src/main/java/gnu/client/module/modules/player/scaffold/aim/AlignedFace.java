package gnu.client.module.modules.player.scaffold.aim;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Axis-aligned face. Port of LB {@code AlignedFace} for 1.8 {@link Vec3}.
 */
public final class AlignedFace {
  private static final double EPS = 1.0e-6;

  public final Vec3 from;
  public final Vec3 to;

  public AlignedFace(Vec3 a, Vec3 b) {
    this.from = new Vec3(
        Math.min(a.xCoord, b.xCoord),
        Math.min(a.yCoord, b.yCoord),
        Math.min(a.zCoord, b.zCoord));
    this.to = new Vec3(
        Math.max(a.xCoord, b.xCoord),
        Math.max(a.yCoord, b.yCoord),
        Math.max(a.zCoord, b.zCoord));
  }

  public double area() {
    Vec3 d = dimensions();
    return d.xCoord * d.yCoord + d.yCoord * d.zCoord + d.xCoord * d.zCoord;
  }

  public Vec3 center() {
    return new Vec3(
        (from.xCoord + to.xCoord) * 0.5,
        (from.yCoord + to.yCoord) * 0.5,
        (from.zCoord + to.zCoord) * 0.5);
  }

  public Vec3 dimensions() {
    return new Vec3(
        to.xCoord - from.xCoord,
        to.yCoord - from.yCoord,
        to.zCoord - from.zCoord);
  }

  public AlignedFace requireNonEmpty() {
    return Math.abs(area()) < EPS ? null : this;
  }

  public AlignedFace truncateY(double minY) {
    return new AlignedFace(
        new Vec3(from.xCoord, Math.max(from.yCoord, minY), from.zCoord),
        new Vec3(to.xCoord, Math.max(to.yCoord, minY), to.zCoord));
  }

  public AlignedFace clamp(AxisAlignedBB box) {
    return new AlignedFace(nearestOnBox(box, from), nearestOnBox(box, to));
  }

  public AlignedFace offset(Vec3 vec) {
    return new AlignedFace(Line3.add(from, vec), Line3.add(to, vec));
  }

  public AlignedFace offset(double x, double y, double z) {
    return offset(new Vec3(x, y, z));
  }

  public Vec3 randomPointOnFace() {
    return new Vec3(
        from.xCoord == to.xCoord ? from.xCoord : lerp(from.xCoord, to.xCoord, Math.random()),
        from.yCoord == to.yCoord ? from.yCoord : lerp(from.yCoord, to.yCoord, Math.random()),
        from.zCoord == to.zCoord ? from.zCoord : lerp(from.zCoord, to.zCoord, Math.random()));
  }

  public NormalizedPlane toPlane() {
    Vec3[] dirs = directionVectors();
    return NormalizedPlane.fromParams(from, dirs[0], dirs[1]);
  }

  public List<LineSegment3> getEdges() {
    ArrayList<LineSegment3> edges = new ArrayList<>(4);
    Vec3[] dirs = directionVectors();
    Vec3 d1 = dirs[0];
    Vec3 d2 = dirs[1];
    if (!Line3.isLikelyZero(d1)) {
      edges.add(new LineSegment3(from, Line3.add(from, d1)));
      edges.add(new LineSegment3(to, Line3.sub(to, d1)));
    }
    if (!Line3.isLikelyZero(d2)) {
      edges.add(new LineSegment3(from, Line3.add(from, d2)));
      edges.add(new LineSegment3(to, Line3.sub(to, d2)));
    }
    return edges;
  }

  /**
   * Simplified LB {@code coerceInFace}: clip supporting line to the two nearest edge hits.
   */
  public LineSegment3 coerceInFace(Line3 line) {
    List<LineSegment3> edges = getEdges();
    ArrayList<Vec3> hits = new ArrayList<>(4);
    ArrayList<Double> dists = new ArrayList<>(4);
    for (LineSegment3 edge : edges) {
      Vec3[] pair = line.nearestPointsTo(edge);
      if (pair == null)
        continue;
      hits.add(pair[1]);
      dists.add(Line3.distSq(pair[0], pair[1]));
    }
    if (hits.size() < 2)
      return null;
    // Sort by distance ascending (nearest edges first)
    Integer[] order = new Integer[hits.size()];
    for (int i = 0; i < order.length; i++)
      order[i] = i;
    java.util.Arrays.sort(order, (i, j) -> Double.compare(dists.get(i), dists.get(j)));
    Vec3 p1 = hits.get(order[0]);
    Vec3 p2 = hits.get(order[1]);
    if (Line3.isLikelyZero(Line3.sub(p2, p1)))
      return null;
    try {
      return new LineSegment3(p1, p2);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public Vec3 nearestPointTo(Line3 otherLine) {
    Vec3[] dirs = directionVectors();
    NormalizedPlane plane = NormalizedPlane.fromParams(from, dirs[0], dirs[1]);
    List<LineSegment3> edges = getEdges();
    Vec3 intersection = plane.intersection(otherLine);
    Vec3 faceCenter = center();

    if (intersection != null) {
      boolean inFace = true;
      for (LineSegment3 edge : edges) {
        Vec3 lineCenter = edge.pointAt(0.5);
        Vec3 toCenter = Line3.sub(lineCenter, faceCenter);
        Vec3 toHit = Line3.sub(lineCenter, intersection);
        if (Line3.dot(toHit, toCenter) <= 0.0) {
          inFace = false;
          break;
        }
      }
      if (edges.isEmpty() || inFace)
        return intersection;
    }

    Vec3 best = null;
    double bestDist = Double.POSITIVE_INFINITY;
    for (LineSegment3 edge : edges) {
      Vec3[] pair = otherLine.nearestPointsTo(edge);
      if (pair == null)
        continue;
      double d = Line3.distSq(pair[0], pair[1]);
      if (d < bestDist) {
        bestDist = d;
        best = pair[1];
      }
    }
    if (best != null)
      return best;
    return intersection != null ? intersection : faceCenter;
  }

  private Vec3[] directionVectors() {
    Vec3 dims = dimensions();
    if (Math.abs(dims.xCoord) < EPS)
      return new Vec3[] {
          new Vec3(0.0, dims.yCoord, 0.0),
          new Vec3(0.0, 0.0, dims.zCoord)};
    if (Math.abs(dims.yCoord) < EPS)
      return new Vec3[] {
          new Vec3(dims.xCoord, 0.0, 0.0),
          new Vec3(0.0, 0.0, dims.zCoord)};
    if (Math.abs(dims.zCoord) < EPS)
      return new Vec3[] {
          new Vec3(0.0, dims.yCoord, 0.0),
          new Vec3(dims.xCoord, 0.0, 0.0)};
    throw new IllegalStateException("Face must be axis-aligned: " + dims);
  }

  public static Vec3 nearestOnBox(AxisAlignedBB box, Vec3 p) {
    return new Vec3(
        clamp(p.xCoord, box.minX, box.maxX),
        clamp(p.yCoord, box.minY, box.maxY),
        clamp(p.zCoord, box.minZ, box.maxZ));
  }

  private static double clamp(double v, double lo, double hi) {
    if (v < lo)
      return lo;
    if (v > hi)
      return hi;
    return v;
  }

  private static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }
}
