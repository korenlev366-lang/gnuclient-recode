package gnu.client.script;

import gnu.client.runtime.mc.Mc;

/** Script-facing {@code util} accessor — shared RNG helpers via {@link Mc}. */
public final class Util {

    public static final Util INSTANCE = new Util();

    private Util() {}

    /** Inclusive random int in {@code [min, max]}. */
    public int randomInt(int min, int max) {
        return Mc.randomInt(min, max);
    }

    /** Random double in {@code [min, max)}. */
    public double randomDouble(double min, double max) {
        return Mc.randomDouble(min, max);
    }
}
