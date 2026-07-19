boolean onPacketReceive(Object packet) {
    if (packets.isSpawnGlobalEntity(packet) && packets.globalEntityType(packet) == 1) {
        Mc.addChatMessage("\u00a7e\u26A1 Lightning detected nearby!");
    }
    return false;
}
