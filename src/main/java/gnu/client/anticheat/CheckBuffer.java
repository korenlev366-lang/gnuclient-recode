package gnu.client.anticheat;

/** OpenMyAU-style decaying violation buffer. */
public final class CheckBuffer {
    private double value;

    public boolean flag(double add, double threshold) {
        value += add;
        return value >= threshold;
    }

    public void decay(double amount) {
        value = Math.max(0.0, value - amount);
    }

    public void reset() {
        value = 0.0;
    }

    public double get() {
        return value;
    }
}
