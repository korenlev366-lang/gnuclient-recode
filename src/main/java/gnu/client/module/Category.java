package gnu.client.module;

// Ordinals are the contract with ClickGUI column order
// (0 Combat, 1 Movement, 2 Player, 3 Visuals, 4 Misc, 5 Settings, 6 Scripts).
// Scripts is shown as a column when that category has modules.
public enum Category {
    COMBAT,
    MOVEMENT,
    PLAYER,
    VISUALS,
    MISC,
    SETTINGS,
    SCRIPTS
}
