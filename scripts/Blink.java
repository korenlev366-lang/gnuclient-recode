java.util.List<Object> blinkBuffer = new java.util.ArrayList<Object>();
boolean blinking = false;

void onPreUpdate() {
    blinking = keybinds.isForwardDown();
}

boolean onPacketSend(Object packet) {
    if (blinking && packets.isMovement(packet)) {
        blinkBuffer.add(packet);
        return true; // cancel (buffer)
    }
    return false;
}

void onScriptDisable() {
    for (int i = 0; i < blinkBuffer.size(); i++) {
        packets.sendReleased(blinkBuffer.get(i));
    }
    blinkBuffer.clear();
    blinking = false;
}
