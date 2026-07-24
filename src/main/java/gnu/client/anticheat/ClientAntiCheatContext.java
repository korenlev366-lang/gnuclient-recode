package gnu.client.anticheat;

/** Callback used by observational checks when another player looks suspicious. */
public interface ClientAntiCheatContext {
    void receiveSignal(String playerName, String cheatName);
}
