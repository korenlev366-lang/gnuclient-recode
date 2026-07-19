void onLoad() {
    modules.registerMode("Mode", 0, "Packet", "Ground");
}

void onPreUpdate() {
    if (client.getMotionY() < -0.5 && modules.isMode("Mode", "Packet")) {
        // Packet hook handles ground flag spoof
    }
}

boolean onPacketSend(Object packet) {
    if (packets.isMovement(packet) && client.getMotionY() < -0.5) {
        if (modules.isMode("Mode", "Packet")) {
            packets.setMovementOnGround(packet, true);
        }
    }
    return false;
}
