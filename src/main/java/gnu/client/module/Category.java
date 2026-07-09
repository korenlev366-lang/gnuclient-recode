package gnu.client.module;

// Ordinals are the contract with the ClickGUI tab bar
// (0 Combat, 1 Player, 2 Visuals, 3 Misc, 4 Settings, 5 Scripts).
// NOTE: 5 Scripts is NOT yet rendered by the ClickGUI tab bar — scripts are
// functional (tick/settings) but GUI-invisible until the ClickGuiScreen
// tab-bar gains a Scripts tab.
public enum Category {
    COMBAT,
    PLAYER,
    VISUALS,
    MISC,
    SETTINGS,
    SCRIPTS
}
