void onLoad() {
    modules.registerSlider("Speed", 2.0f, 0.5f, 10.0f);
}

void onPreUpdate() {
    if (!client.isRiding()) return;
    float yaw = (float) Math.toRadians(client.getYaw());
    double mx = 0, my = 0, mz = 0;
    if (keybinds.isJumpDown()) my = modules.getSlider("Speed") * 0.1;
    if (keybinds.isSneakDown()) my = -modules.getSlider("Speed") * 0.1;
    if (keybinds.isForwardDown()) {
        mx = -Math.sin(yaw) * modules.getSlider("Speed") * 0.05;
        mz = Math.cos(yaw) * modules.getSlider("Speed") * 0.05;
    }
    client.setRidingMotion(mx, my, mz);
    client.sendSteer(0, 0, keybinds.isJumpDown(), false);
}
