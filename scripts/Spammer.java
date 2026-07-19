void onLoad() {
    modules.registerSlider("Delay", 100, 20, 600);
}

int spamTimer = 0;

void onPreUpdate() {
    spamTimer++;
    if (spamTimer >= (int) modules.getSlider("Delay")) {
        spamTimer = 0;
        if (Mc.player() != null) {
            Mc.player().sendChatMessage("Buy GNU Client! The best 1.8.9 client!");
        }
    }
}
