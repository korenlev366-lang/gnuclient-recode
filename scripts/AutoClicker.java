void onLoad() {
    modules.registerSlider("MinCPS", 8.0f, 1.0f, 20.0f);
    modules.registerSlider("MaxCPS", 12.0f, 1.0f, 20.0f);
}

int clickDelay = 0;

void onPreUpdate() {
    if (!keybinds.isMouseDown(0)) return;
    clickDelay--;
    if (clickDelay <= 0) {
        Mc.pressAttackKeyOnce();
        float cps = modules.getSlider("MinCPS")
            + (float) Math.random() * (modules.getSlider("MaxCPS") - modules.getSlider("MinCPS"));
        clickDelay = (int) (20.0f / cps);
    }
}
