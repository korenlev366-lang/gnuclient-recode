void onLoad() {
    modules.registerMode("Mode", 0, "Normal", "Reverse", "Jump");
}

void onPreUpdate() {
    if (modules.isMode("Mode", "Normal") && client.isOnGround() && keybinds.isForwardDown()) {
        Object hit = client.raycastBlock(1.5f, client.getYaw(), 0.0f);
        if (hit != null) {
            client.setMotion(client.getMotionX(), 0.42, client.getMotionZ());
        }
    } else if (modules.isMode("Mode", "Jump")) {
        if (client.isOnGround() && keybinds.isForwardDown()) {
            Object hit = client.raycastBlock(1.5f, client.getYaw(), 0.0f);
            if (hit != null) {
                client.setJump(true);
            }
        }
    }
}
