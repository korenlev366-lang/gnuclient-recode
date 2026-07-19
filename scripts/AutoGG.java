void onLoad() {
    modules.registerSlider("Delay", 40, 10, 100);
}

int autoGgTimer = 0;
boolean shouldGg = false;
String ggMessage = "gg";

boolean onPacketReceive(Object packet) {
    if (packets.isChat(packet)) {
        // Chat content access is limited in the script API
    }
    return false;
}

void onPreUpdate() {
    if (shouldGg) {
        autoGgTimer++;
        if (autoGgTimer >= (int) modules.getSlider("Delay")) {
            if (Mc.player() != null) {
                Mc.player().sendChatMessage(ggMessage);
            }
            shouldGg = false;
            autoGgTimer = 0;
        }
    }
}
