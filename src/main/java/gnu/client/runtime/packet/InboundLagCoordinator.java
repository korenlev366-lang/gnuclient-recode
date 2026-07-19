package gnu.client.runtime.packet;

/**
 * Priority for inbound packet lag modules (highest wins).
 * KnockbackDelay &gt; Lagrange &gt; Backtrack — only the highest-priority active module
 * owns the inbound stream; the others yield so they never queue packets simultaneously.
 */
public final class InboundLagCoordinator {

    public enum Owner {
        NONE,
        KNOCKBACK_DELAY,
        LAGRANGE,
        BACKTRACK
    }

    /** Priority order: higher index wins. KnockbackDelay > Backtrack > Lagrange. */
    private static final Owner[] PRIORITY = {
            Owner.NONE,
            Owner.LAGRANGE,
            Owner.BACKTRACK,
            Owner.KNOCKBACK_DELAY
    };

    private static volatile Owner owner = Owner.NONE;

    private InboundLagCoordinator() {}

    public static Owner getOwner() {
        return owner;
    }

    public static boolean knockbackDelayOwns() {
        return owner == Owner.KNOCKBACK_DELAY;
    }

    public static boolean lagrangeOwns() {
        return owner == Owner.LAGRANGE;
    }

    public static boolean backtrackOwns() {
        return owner == Owner.BACKTRACK;
    }

    /** True if a higher-or-equal priority owner than {@code requested} currently holds the stream. */
    public static boolean isBlockedFor(Owner requested) {
        int reqRank = rankOf(requested);
        int curRank = rankOf(owner);
        return curRank > reqRank;
    }

    private static int rankOf(Owner o) {
        for (int i = 0; i < PRIORITY.length; i++)
            if (PRIORITY[i] == o)
                return i;
        return 0;
    }

    public static boolean tryAcquire(Owner requested) {
        if (requested == Owner.NONE)
            return false;
        // Only acquire if nothing of higher priority owns the stream.
        if (isBlockedFor(requested))
            return false;
        owner = requested;
        return true;
    }

    public static void release(Owner releasing) {
        if (owner == releasing)
            owner = Owner.NONE;
    }

    public static void forceReleaseAll() {
        owner = Owner.NONE;
    }
}
