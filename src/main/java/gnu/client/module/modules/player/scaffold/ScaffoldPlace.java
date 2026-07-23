package gnu.client.module.modules.player.scaffold;

import gnu.client.mixin.impl.accessors.IAccessorEntityPlayerSP;
import gnu.client.runtime.mc.Mc;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Thin world / ray helpers for scaffold place targeting. */
public final class ScaffoldPlace {
    private static final EnumFacing[] BRIDGE_ORDER = {
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST
            // no DOWN/UP — Y faces are PositionPlace-sensitive to bobbing
    };
    private static final EnumFacing[] TOWER_ORDER = {
            EnumFacing.UP,
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST,
            EnumFacing.DOWN
    };
    private static final int EXPAND_XZ = 4;
    private static final int EXPAND_Y_DOWN = 4;
    /** OpenMyau Scaffold.placeOffsets — face sample grid for hang clutch rays. */
    private static final double[] PLACE_OFFSETS = {
            0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375, 0.40625, 0.46875,
            0.53125, 0.59375, 0.65625, 0.71875, 0.78125, 0.84375, 0.90625, 0.96875
    };

    public static final class FaceHit {
        public final float yaw;
        public final float pitch;
        public final Vec3 hit;

        public FaceHit(float yaw, float pitch, Vec3 hit) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.hit = hit;
        }
    }

    private ScaffoldPlace() {}

    /**
     * Block under the player's standing row. Uses {@code posY + 0.2} so same-tick gravity
     * after walking off (e.g. 64.0 → 63.9) still targets the air cell you stepped into,
     * not one row lower ({@code floor(63.9) - 1}).
     */
    public static BlockPos underFeet(EntityPlayer player) {
        if (player == null)
            return null;
        return new BlockPos(player.posX, player.posY + 0.2, player.posZ).down();
    }

    public static boolean isReplaceable(World world, BlockPos pos) {
        if (world == null || pos == null)
            return false;
        Block block = world.getBlockState(pos).getBlock();
        if (!block.getMaterial().isReplaceable())
            return false;
        if (!(block instanceof BlockSnow))
            return true;
        return !(block.getBlockBoundsMaxY() > 0.125);
    }

    public static boolean isValidSupport(World world, BlockPos pos) {
        return pos != null && !isReplaceable(world, pos);
    }

    /** Primary horizontal facing from movement yaw (OpenMyau-style). */
    public static EnumFacing yawToFacing(float yaw) {
        yaw = MathHelper.wrapAngleTo180_float(yaw);
        if (yaw < -135.0f || yaw > 135.0f)
            return EnumFacing.NORTH;
        if (yaw < -45.0f)
            return EnumFacing.EAST;
        if (yaw < 45.0f)
            return EnumFacing.SOUTH;
        return EnumFacing.WEST;
    }

    /** Bridge search: horizontals + down first; UP last. Prefer {@code prefer} face first. */
    public static ScaffoldTarget findNeighborTarget(EntityPlayerSP player, World world, BlockPos under) {
        return findNeighborTarget(player, world, under, false, null);
    }

    public static ScaffoldTarget findNeighborTarget(EntityPlayerSP player, World world,
            BlockPos under, boolean preferUp) {
        return findNeighborTarget(player, world, under, preferUp, null);
    }

    /**
     * Scan neighbors of {@code under} for a solid support whose {@code offset(face)} is the
     * replaceable cell. When {@code preferUp}, try {@link EnumFacing#UP} first (tower).
     * When {@code preferFace} is set, try that horizontal face before the default order.
     */
    public static ScaffoldTarget findNeighborTarget(EntityPlayerSP player, World world,
            BlockPos under, boolean preferUp, EnumFacing preferFace) {
        if (player == null || world == null || under == null)
            return null;
        if (!isReplaceable(world, under))
            return null;
        EnumFacing[] order = preferUp ? TOWER_ORDER : orderedBridgeFaces(preferFace);
        for (EnumFacing face : order) {
            BlockPos support = under.offset(face.getOpposite());
            if (!isValidSupport(world, support))
                continue;
            if (!isReplaceable(world, support.offset(face)))
                continue;
            return new ScaffoldTarget(support, face);
        }
        return null;
    }

    private static EnumFacing[] orderedBridgeFaces(EnumFacing prefer) {
        if (prefer == null || prefer.getAxis() == EnumFacing.Axis.Y)
            return BRIDGE_ORDER;
        EnumFacing[] out = new EnumFacing[BRIDGE_ORDER.length];
        int i = 0;
        out[i++] = prefer;
        for (EnumFacing f : BRIDGE_ORDER) {
            if (f == prefer)
                continue;
            out[i++] = f;
        }
        return out;
    }

    /**
     * Vanilla jump tower: place into the air cell at/above feet against an UP face.
     */
    public static ScaffoldTarget findTowerTarget(EntityPlayerSP player, World world) {
        if (player == null || world == null)
            return null;
        BlockPos feet = new BlockPos(player.posX, player.posY, player.posZ);
        BlockPos under = feet.down();
        if (isReplaceable(world, under)) {
            ScaffoldTarget fromUnder = findNeighborTarget(player, world, under, true);
            if (fromUnder != null)
                return fromUnder;
        }
        if (isReplaceable(world, feet) && isValidSupport(world, under))
            return new ScaffoldTarget(under, EnumFacing.UP);
        BlockPos above = feet.up();
        if (isReplaceable(world, above) && isValidSupport(world, feet))
            return new ScaffoldTarget(feet, EnumFacing.UP);
        return null;
    }

    /**
     * Bridge target under feet, or the next cell(s) in {@code moveYaw} when standing on solid.
     * When hanging: OpenMyau {@code getBlockData} expand (±4) — not only cardinal neighbors.
     */
    public static ScaffoldTarget findBridgeTarget(EntityPlayerSP player, World world,
            BlockPos underFeet, float moveYaw) {
        if (player == null || world == null || underFeet == null)
            return null;
        EnumFacing moveFace = yawToFacing(moveYaw);
        if (isReplaceable(world, underFeet)) {
            ScaffoldTarget under = findNeighborTarget(player, world, underFeet, false, moveFace);
            if (under != null)
                return under;
            return findHangTarget(player, world, underFeet);
        }
        double rad = Math.toRadians(moveYaw);
        int ox = (int) Math.round(-Math.sin(rad));
        int oz = (int) Math.round(Math.cos(rad));
        int sx = (int) Math.signum(-Math.sin(rad));
        int sz = (int) Math.signum(Math.cos(rad));
        if (sx == 0 && sz == 0 && ox == 0 && oz == 0)
            return null;

        BlockPos[] ahead = new BlockPos[] {
            (ox != 0 || sx != 0) ? underFeet.add(ox != 0 ? ox : sx, 0, 0) : null,
            (oz != 0 || sz != 0) ? underFeet.add(0, 0, oz != 0 ? oz : sz) : null,
            (sx != 0 && sz != 0) ? underFeet.add(sx, 0, sz) : null,
            (ox != 0 && oz != 0) ? underFeet.add(ox, 0, oz) : null,
        };
        ScaffoldTarget bestVisible = null;
        ScaffoldTarget bestAny = null;
        double bestVisDist = Double.MAX_VALUE;
        double bestAnyDist = Double.MAX_VALUE;
        for (BlockPos cell : ahead) {
            if (cell == null || !isReplaceable(world, cell))
                continue;
            ScaffoldTarget t = findNeighborTarget(player, world, cell, false, moveFace);
            if (t == null)
                continue;
            double dist = player.getDistanceSq(
                cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
            if (dist < bestAnyDist) {
                bestAnyDist = dist;
                bestAny = t;
            }
            if (canSeeFace(player, t.support, t.face) && dist < bestVisDist) {
                bestVisDist = dist;
                bestVisible = t;
            }
        }
        return bestVisible != null ? bestVisible : bestAny;
    }

    /**
     * OpenMyau {@code getBlockData} + {@code getBestFacing}: nearest solid in ±4 that can
     * place toward under-feet <b>and</b> passes Grim PositionPlace ({@link #canSeeFace}).
     */
    public static ScaffoldTarget findHangTarget(EntityPlayerSP player, World world,
            BlockPos underFeet) {
        if (player == null || world == null || underFeet == null)
            return null;
        if (!isReplaceable(world, underFeet))
            return null;
        float reach = 4.5f;
        if (Mc.controller() != null)
            reach = Mc.controller().getBlockReachDistance();
        double reachSq = (double) reach * (double) reach;
        java.util.ArrayList<BlockPos> supports = new java.util.ArrayList<BlockPos>();
        for (int x = -EXPAND_XZ; x <= EXPAND_XZ; x++) {
            for (int y = -EXPAND_Y_DOWN; y <= 0; y++) {
                for (int z = -EXPAND_XZ; z <= EXPAND_XZ; z++) {
                    if (x == 0 && y == 0 && z == 0)
                        continue;
                    BlockPos support = underFeet.add(x, y, z);
                    if (!isValidSupport(world, support))
                        continue;
                    double distSq = player.getDistanceSq(
                        support.getX() + 0.5, support.getY() + 0.5, support.getZ() + 0.5);
                    if (distSq > reachSq)
                        continue;
                    boolean any = false;
                    for (EnumFacing face : EnumFacing.VALUES) {
                        if (face == EnumFacing.DOWN)
                            continue;
                        if (isReplaceable(world, support.offset(face))) {
                            any = true;
                            break;
                        }
                    }
                    if (any)
                        supports.add(support);
                }
            }
        }
        if (supports.isEmpty())
            return null;
        final double tx = underFeet.getX() + 0.5;
        final double ty = underFeet.getY() + 0.5;
        final double tz = underFeet.getZ() + 0.5;
        supports.sort(java.util.Comparator.comparingDouble(
            o -> o.distanceSqToCenter(tx, ty, tz)));

        ScaffoldTarget bestUnderSee = null;
        ScaffoldTarget bestUnder = null;
        ScaffoldTarget bestSee = null;
        ScaffoldTarget bestAny = null;
        double bestSeeDist = Double.MAX_VALUE;
        double bestAnyDist = Double.MAX_VALUE;
        for (BlockPos support : supports) {
            EnumFacing face = bestFacingToward(support, underFeet);
            if (face == null)
                continue;
            BlockPos placed = support.offset(face);
            if (!isReplaceable(world, placed))
                continue;
            ScaffoldTarget t = new ScaffoldTarget(support, face);
            boolean see = canSeeFace(player, support, face);
            if (placed.equals(underFeet)) {
                if (see) {
                    bestUnderSee = t;
                    break;
                }
                if (bestUnder == null)
                    bestUnder = t;
                continue;
            }
            double dist = support.distanceSqToCenter(tx, ty, tz);
            if (see && dist < bestSeeDist) {
                bestSeeDist = dist;
                bestSee = t;
            }
            if (dist < bestAnyDist) {
                bestAnyDist = dist;
                bestAny = t;
            }
        }
        if (bestUnderSee != null)
            return bestUnderSee;
        if (bestUnder != null)
            return bestUnder;
        if (bestSee != null)
            return bestSee;
        return bestAny;
    }

    /** OpenMyau {@code getBestFacing}: place cell closest to {@code toward}, Y ≤ toward. */
    public static EnumFacing bestFacingToward(BlockPos support, BlockPos toward) {
        if (support == null || toward == null)
            return null;
        double best = Double.MAX_VALUE;
        EnumFacing bestFace = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing == EnumFacing.DOWN)
                continue;
            BlockPos placed = support.offset(facing);
            if (placed.getY() > toward.getY())
                continue;
            double dist = placed.distanceSqToCenter(
                toward.getX() + 0.5, toward.getY() + 0.5, toward.getZ() + 0.5);
            if (bestFace == null || dist < best
                    || (dist == best && facing == EnumFacing.UP)) {
                best = dist;
                bestFace = facing;
            }
        }
        return bestFace;
    }

    /**
     * @deprecated prefer {@link #findHangTarget} (OpenMyau getBlockData parity)
     */
    public static ScaffoldTarget findExpandTarget(EntityPlayerSP player, World world,
            BlockPos underFeet, float moveYaw) {
        return findHangTarget(player, world, underFeet);
    }

    public static float[] rotationsTo(Vec3 point, EntityPlayer player, float baseYaw, float basePitch) {
        if (point == null || player == null)
            return new float[] { baseYaw, basePitch };
        return rotationsToAt(point,
            player.posX, player.posY + player.getEyeHeight(), player.posZ,
            baseYaw, basePitch);
    }

    public static float[] rotationsToAt(Vec3 point, double eyeX, double eyeY, double eyeZ,
            float baseYaw, float basePitch) {
        if (point == null)
            return new float[] { baseYaw, basePitch };
        double dx = point.xCoord - eyeX;
        double dy = point.yCoord - eyeY;
        double dz = point.zCoord - eyeZ;
        double dist = MathHelper.sqrt_double(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(dy, dist) * 180.0 / Math.PI));
        yaw = baseYaw + ScaffoldRotations.wrap(yaw - baseYaw);
        pitch = ScaffoldRotations.clampPitch(basePitch + ScaffoldRotations.wrap(pitch - basePitch));
        return new float[] { yaw, pitch };
    }

    public static Vec3 findPlacementHit(EntityPlayer player, BlockPos support, EnumFacing face,
            float yaw, float pitch) {
        if (player == null)
            return null;
        return findPlacementHitAt(player.worldObj, support, face,
            player.posX, player.posY + player.getEyeHeight(), player.posZ, yaw, pitch);
    }

    /**
     * OpenMyau placeOffsets search: sample points on the face, pick look that rays the
     * support+face from current eyes. When {@code hardAway}, only accept back hemisphere.
     */
    public static FaceHit findFaceHit(EntityPlayer player, BlockPos support, EnumFacing face,
            float baseYaw, float basePitch, float moveYaw, boolean hardAway) {
        if (player == null || support == null || face == null || player.worldObj == null)
            return null;
        double[] xs = PLACE_OFFSETS;
        double[] ys = PLACE_OFFSETS;
        double[] zs = PLACE_OFFSETS;
        switch (face) {
            case NORTH:
                zs = new double[] { 0.0 };
                break;
            case SOUTH:
                zs = new double[] { 1.0 };
                break;
            case WEST:
                xs = new double[] { 0.0 };
                break;
            case EAST:
                xs = new double[] { 1.0 };
                break;
            case DOWN:
                ys = new double[] { 0.0 };
                break;
            case UP:
                ys = new double[] { 1.0 };
                break;
            default:
                break;
        }
        float reach = 4.5f;
        if (Mc.controller() != null)
            reach = Mc.controller().getBlockReachDistance();
        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;
        FaceHit best = null;
        float bestDiff = Float.MAX_VALUE;
        for (double dx : xs) {
            for (double dy : ys) {
                for (double dz : zs) {
                    double px = support.getX() + dx;
                    double py = support.getY() + dy;
                    double pz = support.getZ() + dz;
                    float[] rot = rotationsToAt(new Vec3(px, py, pz), eyeX, eyeY, eyeZ, baseYaw, basePitch);
                    if (hardAway && !Float.isNaN(moveYaw)
                            && Math.abs(ScaffoldRotations.wrap(rot[0] - moveYaw)) < 90f)
                        continue;
                    MovingObjectPosition mop = rayTraceAt(
                        player.worldObj, eyeX, eyeY, eyeZ, rot[0], rot[1], reach);
                    if (mop == null
                            || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                            || mop.getBlockPos() == null
                            || !mop.getBlockPos().equals(support)
                            || mop.sideHit != face)
                        continue;
                    float diff = Math.abs(ScaffoldRotations.wrap(rot[0] - baseYaw))
                            + Math.abs(rot[1] - basePitch);
                    if (best == null || diff < bestDiff) {
                        best = new FaceHit(rot[0], rot[1], mop.hitVec);
                        bestDiff = diff;
                    }
                }
            }
        }
        return best;
    }

    /** OpenMyau {@code BlockUtil.getClickVec} — face center-ish click without ray. */
    public static Vec3 clickVec(BlockPos support, EnumFacing face) {
        if (support == null || face == null)
            return null;
        double x = support.getX() + 0.5;
        double y = support.getY() + 0.5;
        double z = support.getZ() + 0.5;
        switch (face) {
            case DOWN:
                y = support.getY();
                break;
            case UP:
                y = support.getY() + 1.0;
                break;
            case NORTH:
                z = support.getZ();
                break;
            case SOUTH:
                z = support.getZ() + 1.0;
                break;
            case WEST:
                x = support.getX();
                break;
            case EAST:
                x = support.getX() + 1.0;
                break;
            default:
                break;
        }
        return new Vec3(x, y, z);
    }

    public static Vec3 findPlacementHitAt(World world, BlockPos support, EnumFacing face,
            double eyeX, double eyeY, double eyeZ, float yaw, float pitch) {
        if (world == null || support == null || face == null)
            return null;
        float reach = 4.5f;
        if (Mc.controller() != null)
            reach = Mc.controller().getBlockReachDistance();
        MovingObjectPosition mop = rayTraceAt(world, eyeX, eyeY, eyeZ, yaw, pitch, reach);
        if (mop == null
                || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || mop.getBlockPos() == null
                || !mop.getBlockPos().equals(support)
                || mop.sideHit != face)
            return null;
        return mop.hitVec;
    }

    /**
     * Prefer {@code preferYaw}/{@code preferPitch}; if that misses, search nearby pitch then yaw.
     */
    public static float[] findHittingLook(EntityPlayer player, BlockPos support, EnumFacing face,
            float preferYaw, float preferPitch) {
        return findHittingLook(player, support, face, preferYaw, preferPitch, Float.NaN, false);
    }

    /**
     * When {@code preferAwayFromMove}, only accept hits in the backwards hemisphere
     * ({@code |yaw - moveYaw| >= 90}). No forward-look fallback — skip place instead.
     */
    public static float[] findHittingLook(EntityPlayer player, BlockPos support, EnumFacing face,
            float preferYaw, float preferPitch, float moveYaw, boolean preferAwayFromMove) {
        if (player == null)
            return null;
        return findHittingLookAt(player.worldObj, support, face,
            player.posX, player.posY + player.getEyeHeight(), player.posZ,
            preferYaw, preferPitch, moveYaw, preferAwayFromMove);
    }

    /** Like {@link #findHittingLook} but rays from an explicit eye (predicted pre-move pos). */
    public static float[] findHittingLookAt(World world, BlockPos support, EnumFacing face,
            double eyeX, double eyeY, double eyeZ,
            float preferYaw, float preferPitch, float moveYaw, boolean preferAwayFromMove) {
        if (isAcceptableLook(preferYaw, moveYaw, preferAwayFromMove)
                && findPlacementHitAt(world, support, face, eyeX, eyeY, eyeZ, preferYaw, preferPitch) != null)
            return new float[] { preferYaw, preferPitch };

        float[] best = null;
        float bestScore = Float.MAX_VALUE;

        Vec3 center = faceCenter(support, face);
        if (center != null) {
            float[] toCenter = rotationsToAt(center, eyeX, eyeY, eyeZ, preferYaw, preferPitch);
            float[] c1 = scoreHitAt(world, support, face, eyeX, eyeY, eyeZ, preferYaw, toCenter[1],
                moveYaw, preferAwayFromMove, preferYaw, preferPitch);
            if (c1 != null) {
                best = new float[] { c1[0], c1[1] };
                bestScore = c1[2];
            }
            float[] c2 = scoreHitAt(world, support, face, eyeX, eyeY, eyeZ, toCenter[0], toCenter[1],
                moveYaw, preferAwayFromMove, preferYaw, preferPitch);
            if (c2 != null && c2[2] < bestScore) {
                best = new float[] { c2[0], c2[1] };
                bestScore = c2[2];
            }
        }

        for (float dPitch = -20f; dPitch <= 20f; dPitch += 2f) {
            float pitch = ScaffoldRotations.clampPitch(preferPitch + dPitch);
            float[] s = scoreHitAt(world, support, face, eyeX, eyeY, eyeZ, preferYaw, pitch,
                moveYaw, preferAwayFromMove, preferYaw, preferPitch);
            if (s != null && s[2] < bestScore) {
                best = new float[] { s[0], s[1] };
                bestScore = s[2];
            }
        }
        for (float dYaw = -90f; dYaw <= 90f; dYaw += 5f) {
            float yaw = preferYaw + dYaw;
            for (float dPitch = -35f; dPitch <= 35f; dPitch += 5f) {
                float pitch = ScaffoldRotations.clampPitch(preferPitch + dPitch);
                float[] s = scoreHitAt(world, support, face, eyeX, eyeY, eyeZ, yaw, pitch,
                    moveYaw, preferAwayFromMove, preferYaw, preferPitch);
                if (s != null && s[2] < bestScore) {
                    best = new float[] { s[0], s[1] };
                    bestScore = s[2];
                }
            }
        }
        return best;
    }

    private static boolean isAcceptableLook(float yaw, float moveYaw, boolean preferAwayFromMove) {
        if (!preferAwayFromMove || Float.isNaN(moveYaw))
            return true;
        return Math.abs(ScaffoldRotations.wrap(yaw - moveYaw)) >= 90f;
    }

    /** @return {yaw, pitch, score} or null if no hit / forward look rejected */
    private static float[] scoreHit(EntityPlayer player, BlockPos support, EnumFacing face,
            float yaw, float pitch, float moveYaw, boolean preferAwayFromMove,
            float preferYaw, float preferPitch) {
        if (player == null)
            return null;
        return scoreHitAt(player.worldObj, support, face,
            player.posX, player.posY + player.getEyeHeight(), player.posZ,
            yaw, pitch, moveYaw, preferAwayFromMove, preferYaw, preferPitch);
    }

    private static float[] scoreHitAt(World world, BlockPos support, EnumFacing face,
            double eyeX, double eyeY, double eyeZ,
            float yaw, float pitch, float moveYaw, boolean preferAwayFromMove,
            float preferYaw, float preferPitch) {
        if (!isAcceptableLook(yaw, moveYaw, preferAwayFromMove))
            return null;
        if (findPlacementHitAt(world, support, face, eyeX, eyeY, eyeZ, yaw, pitch) == null)
            return null;
        float score = Math.abs(ScaffoldRotations.wrap(yaw - preferYaw))
                + 0.25f * Math.abs(pitch - preferPitch);
        if (preferAwayFromMove && !Float.isNaN(moveYaw)) {
            float fromMove = Math.abs(ScaffoldRotations.wrap(yaw - moveYaw));
            score -= (fromMove - 90f) * 0.5f;
        }
        return new float[] { yaw, pitch, score };
    }

    /**
     * Grim {@code PositionPlace} at the position Grim still has when C08 arrives:
     * {@code lastReportedPos*} (last flying), <b>not</b> current client pos after the
     * previous tick's move — that one-step desync was the remaining PositionPlace source.
     *
     * <p>Camera view-bobbing is irrelevant. Silent-aim flicker is separate (hold look).
     */
    public static boolean canSeeFace(EntityPlayer player, BlockPos support, EnumFacing face) {
        if (player == null || support == null || face == null)
            return false;
        double eyeX = player.posX;
        double eyeY = player.posY;
        double eyeZ = player.posZ;
        if (player instanceof IAccessorEntityPlayerSP) {
            IAccessorEntityPlayerSP acc = (IAccessorEntityPlayerSP) player;
            eyeX = acc.getLastReportedPosX();
            eyeY = acc.getLastReportedPosY();
            eyeZ = acc.getLastReportedPosZ();
        }
        return canSeeFaceAt(eyeX, eyeY, eyeZ, support, face);
    }

    /**
     * Grim {@code RotationPlace} pre-flying: ray from lastReported eyes with the look
     * Grim still has (last flying yaw/pitch) must hit the support box.
     */
    public static boolean canRotationHit(EntityPlayer player, BlockPos support,
            float yaw, float pitch) {
        if (player == null || support == null || player.worldObj == null)
            return false;
        double posX = player.posX;
        double posY = player.posY;
        double posZ = player.posZ;
        if (player instanceof IAccessorEntityPlayerSP) {
            IAccessorEntityPlayerSP acc = (IAccessorEntityPlayerSP) player;
            posX = acc.getLastReportedPosX();
            posY = acc.getLastReportedPosY();
            posZ = acc.getLastReportedPosZ();
        }
        float reach = 4.5f;
        if (Mc.controller() != null)
            reach = Mc.controller().getBlockReachDistance();
        double minX = support.getX();
        double maxX = minX + 1.0;
        double minY = support.getY();
        double maxY = minY + 1.0;
        double minZ = support.getZ();
        double maxZ = minZ + 1.0;
        final double eps = 1.0E-7;
        // Grim possibleEyeHeights 1.8: 1.62 and 1.62-0.08
        for (double eyeH : new double[] { 1.62, 1.62 - 0.08 }) {
            double eyeY = posY + eyeH;
            if (posX > minX + eps && posX < maxX - eps
                    && posZ > minZ + eps && posZ < maxZ - eps
                    && eyeY > minY + eps && eyeY < maxY - eps)
                return true;
            MovingObjectPosition mop = rayTraceAt(
                player.worldObj, posX, eyeY, posZ, yaw, pitch, reach);
            if (mop != null
                    && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    && mop.getBlockPos() != null
                    && mop.getBlockPos().equals(support))
                return true;
        }
        return false;
    }

    /** Placement hitVec from lastReported eyes (Grim still has this pos when C08 arrives). */
    public static Vec3 findPlacementHitLastReported(EntityPlayer player, BlockPos support,
            EnumFacing face, float yaw, float pitch) {
        if (player == null || support == null || face == null || player.worldObj == null)
            return null;
        double posX = player.posX;
        double posY = player.posY;
        double posZ = player.posZ;
        if (player instanceof IAccessorEntityPlayerSP) {
            IAccessorEntityPlayerSP acc = (IAccessorEntityPlayerSP) player;
            posX = acc.getLastReportedPosX();
            posY = acc.getLastReportedPosY();
            posZ = acc.getLastReportedPosZ();
        }
        return findPlacementHitAt(player.worldObj, support, face,
            posX, posY + 1.62, posZ, yaw, pitch);
    }

    /** Half-space check at an explicit eye base position (feet pos, eyes = y + height). */
    public static boolean canSeeFaceAt(double posX, double posY, double posZ,
            BlockPos support, EnumFacing face) {
        if (support == null || face == null)
            return false;

        double minX = support.getX();
        double maxX = minX + 1.0;
        double minY = support.getY();
        double maxY = minY + 1.0;
        double minZ = support.getZ();
        double maxZ = minZ + 1.0;

        // Grim 1.8 possibleEyeHeights: 1.62 and 1.62-0.08
        double eyeYMin = posY + (1.62 - 0.08);
        double eyeYMax = posY + 1.62;
        double eyeX = posX;
        double eyeZ = posZ;

        // Grim isIntersected (COLLISION_EPSILON): eye segment strictly inside support.
        final double eps = 1.0E-7;
        if (eyeX > minX + eps && eyeX < maxX - eps
                && eyeZ > minZ + eps && eyeZ < maxZ - eps
                && eyeYMax > minY + eps && eyeYMin < maxY - eps)
            return true;

        // Exact Grim flag invert (movementThreshold 0) — no extra margin.
        switch (face) {
            case NORTH:
                return !(eyeZ > minZ);
            case SOUTH:
                return !(eyeZ < maxZ);
            case WEST:
                return !(eyeX > minX);
            case EAST:
                return !(eyeX < maxX);
            case UP:
                return !(eyeYMax < maxY);
            case DOWN:
                return !(eyeYMin > minY);
            default:
                return false;
        }
    }

    /**
     * Under-feet / tower / hang path-fill (OpenMyau getBestFacing may place a same-Y
     * neighbor first, then underFeet on multiplace).
     */
    public static boolean isSafePlaceTarget(EntityPlayer player, ScaffoldTarget target,
            BlockPos underFeet, boolean allowPathFill) {
        if (player == null || target == null || underFeet == null || target.placed == null)
            return false;
        if (target.face == EnumFacing.DOWN)
            return false;
        if (target.placed.equals(underFeet))
            return true;
        if (target.face == EnumFacing.UP) {
            int bx = MathHelper.floor_double(player.posX);
            int bz = MathHelper.floor_double(player.posZ);
            int by = MathHelper.floor_double(player.posY + 0.2);
            if (target.placed.getX() == bx && target.placed.getZ() == bz
                    && target.placed.getY() <= by)
                return true;
        }
        if (!allowPathFill)
            return false;
        if (target.placed.getY() > underFeet.getY())
            return false;
        int dx = Math.abs(target.placed.getX() - underFeet.getX());
        int dz = Math.abs(target.placed.getZ() - underFeet.getZ());
        // OpenMyau multiplace path: same row / nearby cells toward underFeet.
        return dx <= 2 && dz <= 2 && (dx + dz) <= 3;
    }

    /**
     * True if placing would put a full cube inside the player body (ghost / Simulation).
     * Tower UP into the feet column is allowed.
     */
    public static boolean wouldIntersectPlayer(EntityPlayer player, BlockPos support, EnumFacing face) {
        if (player == null || support == null || face == null)
            return false;
        BlockPos placed = support.offset(face);
        // Under-feet clutch: after walking off, AABB already overlaps that cell — OpenMyau
        // does not cancel; blocking here skipped the only place that stops the fall.
        BlockPos under = underFeet(player);
        if (under != null && placed.equals(under))
            return false;
        if (face == EnumFacing.UP) {
            int footY = MathHelper.floor_double(player.posY + 0.2);
            if (placed.getY() <= footY)
                return false;
        }
        AxisAlignedBB placedBox = new AxisAlignedBB(
            placed.getX(), placed.getY(), placed.getZ(),
            placed.getX() + 1.0, placed.getY() + 1.0, placed.getZ() + 1.0);
        AxisAlignedBB playerBox = player.getEntityBoundingBox();
        if (playerBox == null)
            return false;
        if (placedBox.maxY <= playerBox.minY + 0.05)
            return false;
        playerBox = playerBox.expand(0.02, 0.0, 0.02);
        return playerBox.intersectsWith(placedBox);
    }

    /**
     * Predict vanilla {@code ItemBlock} / controller rejection before
     * {@code onPlayerRightClick} (which still sends C08 on many fail paths).
     */
    public static boolean wouldFailVanillaPlace(EntityPlayer player, World world,
            BlockPos support, EnumFacing face) {
        if (player == null || world == null || support == null || face == null)
            return true;
        BlockPos placed = support.offset(face);
        if (!isReplaceable(world, placed))
            return true;
        BlockPos under = underFeet(player);
        if (under != null && placed.equals(under))
            return false;
        AxisAlignedBB box = new AxisAlignedBB(
            placed.getX(), placed.getY(), placed.getZ(),
            placed.getX() + 1.0, placed.getY() + 1.0, placed.getZ() + 1.0);
        // Self-collision at/above feet while standing — vanilla ItemBlock rejects these
        // but controller still sends C08 on many paths.
        AxisAlignedBB playerBox = player.getEntityBoundingBox();
        if (playerBox != null) {
            AxisAlignedBB shrink = box.contract(0.05, 0.05, 0.05);
            if (playerBox.intersectsWith(shrink)) {
                int footY = MathHelper.floor_double(player.posY + 0.2);
                // Allow under-feet clutch only; reject tower-into-body.
                if (under == null || !placed.equals(under)) {
                    if (placed.getY() >= footY && player.onGround)
                        return true;
                    if (placed.getY() > footY)
                        return true;
                }
            }
        }
        if (!world.checkNoEntityCollision(box, player))
            return true;
        return false;
    }

    /** @deprecated use {@link #findHittingLook} + {@link ScaffoldRotations#stepToward} */
    public static float[] findHittingRotation(EntityPlayer player, BlockPos support, EnumFacing face,
            float preferYaw, float preferPitch, float baseYaw, float basePitch) {
        float[] look = findHittingLook(player, support, face, preferYaw, preferPitch);
        if (look == null)
            return null;
        return ScaffoldRotations.stepToward(baseYaw, basePitch, look[0], look[1], 100);
    }

    public static MovingObjectPosition rayTrace(EntityPlayer player, float yaw, float pitch, float reach) {
        if (player == null)
            return null;
        return rayTraceAt(player.worldObj,
            player.posX, player.posY + player.getEyeHeight(), player.posZ,
            yaw, pitch, reach);
    }

    public static MovingObjectPosition rayTraceAt(World world, double eyeX, double eyeY, double eyeZ,
            float yaw, float pitch, float reach) {
        if (world == null)
            return null;
        // Client interact ray from the given eye. canSeeFace stays on lastReportedPos*.
        Vec3 start = new Vec3(eyeX, eyeY, eyeZ);
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float cosPitch = MathHelper.cos(pitchRad);
        float dx = -MathHelper.sin(yawRad) * cosPitch;
        float dy = -MathHelper.sin(pitchRad);
        float dz = MathHelper.cos(yawRad) * cosPitch;
        Vec3 end = start.addVector(dx * reach, dy * reach, dz * reach);
        return world.rayTraceBlocks(start, end, false, false, false);
    }

    public static Vec3 faceCenter(BlockPos support, EnumFacing face) {
        if (support == null || face == null)
            return null;
        double x = support.getX() + 0.5;
        double y = support.getY() + 0.5;
        double z = support.getZ() + 0.5;
        switch (face) {
            case DOWN:
                y = support.getY();
                break;
            case UP:
                y = support.getY() + 1.0;
                break;
            case NORTH:
                z = support.getZ();
                break;
            case SOUTH:
                z = support.getZ() + 1.0;
                break;
            case WEST:
                x = support.getX();
                break;
            case EAST:
                x = support.getX() + 1.0;
                break;
            default:
                break;
        }
        return new Vec3(x, y, z);
    }
}
