void onLoad() {
    modules.registerMode("Mode", 0, "None", "Vanilla", "Custom");
    modules.registerSlider("CustomSlow", 0.6f, 0.0f, 1.0f);
}

float itemUseSlowTarget() {
    if (modules.isMode("Mode", "None")) return 1.0f;
    if (modules.isMode("Mode", "Vanilla")) return -1.0f; // skip
    return modules.getSlider("CustomSlow");
}
