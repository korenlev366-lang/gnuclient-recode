void onLoad() {
    modules.registerMode("Mode", 0, "NCP", "Grim", "Watchdog");
}

boolean onPacketSend(Object packet) {
    if (modules.isMode("Mode", "NCP")) {
        // Drop RELEASE_USE_ITEM — common NCP item-use sync edge
        if (packets.isReleaseUseItem(packet)) {
            return true;
        }
    }
    if (modules.isMode("Mode", "Grim")) {
        // Cancel sprint action packets briefly after dig to avoid BadPacketsX windows
        if (packets.isSprintEntityAction(packet) && keybinds.isMouseDown(0)) {
            return true;
        }
    }
    if (modules.isMode("Mode", "Watchdog")) {
        if (packets.isClientTransaction(packet)) {
            short uid = packets.transactionUid(packet);
            packets.setTransactionUid(packet, (short) (uid + 1));
        }
    }
    return false;
}

void onPreUpdate() {
    lenience.decayTick();
}
