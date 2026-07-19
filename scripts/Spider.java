void onLoad() {
    modules.registerSlider("Speed", 0.3f, 0.1f, 1.0f);
}

void onPreUpdate() {
    if (keybinds.isForwardDown() && !client.isOnGround()) {
        Object hit = client.raycastBlock(0.6f, client.getYaw(), 0.0f);
        if (hit != null) {
            client.setMotion(client.getMotionX(),
                modules.getSlider("Speed"), client.getMotionZ());
        }
    }
}
