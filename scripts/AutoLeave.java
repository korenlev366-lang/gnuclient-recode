void onLoad() {
    modules.registerSlider("Health", 4.0f, 1.0f, 19.0f);
}

void onPreUpdate() {
    if (status.getHealth() <= modules.getSlider("Health")) {
        Mc.addChatMessage("\u00a7c[AutoLeave] Health critical, disconnecting...");
        if (Mc.player() != null) {
            Mc.player().sendChatMessage("/logout");
        }
    }
}
