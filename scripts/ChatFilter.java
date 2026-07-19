boolean onPacketReceive(Object packet) {
    if (packets.isChat(packet)) {
        // Packet content inspection is limited; hook is ready when chat text is exposed
    }
    return false;
}
