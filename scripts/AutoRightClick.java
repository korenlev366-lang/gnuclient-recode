void onLoad() {
    modules.registerSlider("CPS", 4.0f, 1.0f, 10.0f);
}

int rcDelay = 0;

void onPreUpdate() {
    if (!keybinds.isMouseDown(1)) { rcDelay = 0; return; }
    rcDelay--;
    if (rcDelay <= 0) {
        keybinds.rightClick();
        rcDelay = (int) (20.0f / modules.getSlider("CPS"));
    }
}
