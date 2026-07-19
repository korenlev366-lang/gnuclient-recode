void onPreUpdate() {
    lenience.decayTick();
}

boolean onPacketReceive(Object packet) {
    if (packets.isPlayerPosLook(packet)) {
        lenience.setSetbackTicks(10);
        double x = packets.posLookX(packet);
        double y = packets.posLookY(packet);
        double z = packets.posLookZ(packet);
        client.setPlayerPosition(x, y, z);
    }
    return false;
}
