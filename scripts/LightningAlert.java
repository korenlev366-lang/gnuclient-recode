boolean onPacketReceive(Object packet) {
    if (packets.simpleName(packet).contains("SpawnGlobalEntity")) {
        Mc.addChatMessage("\u00a7e\u26A1 Lightning detected nearby!");
    }
    return false;
}
