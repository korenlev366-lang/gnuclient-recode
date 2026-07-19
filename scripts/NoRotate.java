boolean onPacketReceive(Object packet) {
    if (packets.isPlayerPosLook(packet)) {
        // Keep our own rotations; accept position from the setback
        client.setRotation(client.getYaw(), client.getPitch());
    }
    return false;
}
