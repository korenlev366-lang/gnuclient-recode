void onLoad() {
    modules.registerSlider("Speed", 2.0f, 0.5f, 10.0f);
}

void onPreUpdate() {
    client.setTimerSpeed(modules.getSlider("Speed"));
}

void onScriptDisable() {
    client.resetTimer();
}
