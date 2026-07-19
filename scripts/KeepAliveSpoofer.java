void onLoad() {
    modules.registerSlider("Offset", 1, -1000, 1000);
}

boolean onPacketSend(Object packet) {
    if (packets.isKeepAlive(packet)) {
        int id = packets.keepAliveId(packet);
        packets.setKeepAliveId(packet, id + (int) modules.getSlider("Offset"));
    }
    return false;
}
