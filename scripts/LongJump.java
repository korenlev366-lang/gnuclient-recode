void onLoad() {
    modules.registerSlider("Boost", 1.5f, 1.0f, 4.0f);
}

boolean jumped = false;

void onPreUpdate() {
    if (client.isOnGround()) jumped = false;
    if (keybinds.isJumpDown() && client.isOnGround() && !jumped) {
        float boost = modules.getSlider("Boost");
        double mx = client.getMotionX() * boost;
        double mz = client.getMotionZ() * boost;
        client.setMotion(mx, 0.42, mz);
        jumped = true;
    }
}
