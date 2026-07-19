boolean onPacketReceive(Object packet) {
    if (packets.isPlayerPosLook(packet)) {
        packets.setPosLookRotation(packet, client.getYaw(), client.getPitch());
    }
    return false;
}
