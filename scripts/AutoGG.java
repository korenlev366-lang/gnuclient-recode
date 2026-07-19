void onLoad() {
    modules.registerSlider("Delay", 40, 10, 100);
}

int autoGgTimer = 0;
boolean shouldGg = false;
String ggMessage = "gg";

boolean onPacketReceive(Object packet) {
    if (packets.isChatReceive(packet)) {
        String text = packets.chatText(packet);
        if (text == null) return false;
        String lower = text.toLowerCase();
        if (lower.contains("winner") || lower.contains("victory") || lower.contains("game over")
                || lower.contains("1st killer") || lower.contains("you won")
                || lower.contains("winning team")) {
            shouldGg = true;
            autoGgTimer = 0;
        }
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
