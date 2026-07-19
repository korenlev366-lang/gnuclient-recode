void onLoad() {
    modules.registerSlider("Timer", 3.0f, 1.0f, 10.0f);
    modules.registerSlider("VerticalSpeed", 2.0f, 0.5f, 10.0f);
}

java.util.List<Object> blinkFlyBuf = new java.util.ArrayList<Object>();

void onPreUpdate() {
    client.setTimerSpeed(modules.getSlider("Timer"));
    if (keybinds.isJumpDown()) {
        client.setMotion(client.getMotionX(), modules.getSlider("VerticalSpeed") * 0.1, client.getMotionZ());
    }
    if (keybinds.isSneakDown()) {
        client.setMotion(client.getMotionX(), -modules.getSlider("VerticalSpeed") * 0.1, client.getMotionZ());
    }
}

boolean onPacketSend(Object packet) {
    if (packets.isMovement(packet)) {
        blinkFlyBuf.add(packet);
        return true;
    }
    return false;
}

void onScriptDisable() {
    client.resetTimer();
    for (int i = 0; i < blinkFlyBuf.size(); i++) {
        packets.sendReleased(blinkFlyBuf.get(i));
    }
    blinkFlyBuf.clear();
}
