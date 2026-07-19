boolean onPacketSend(Object packet) {
    if (packets.isKeepAlive(packet)) {
        // Could modify keepalive ID here for lag-switch experiments
    }
    return false;
}
