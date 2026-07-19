void onPreUpdate() {
    if (client.isOnGround() && !status.isInWater() && !client.isSneaking()) {
        if (keybinds.isMouseDown(0)) {
            client.setMotion(client.getMotionX(), 0.1, client.getMotionZ());
            client.setOnGround(false);
        }
    }
}
