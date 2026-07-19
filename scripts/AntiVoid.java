void onLoad() {
    modules.registerSlider("Height", 5.0f, 0.0f, 20.0f);
}

void onPreUpdate() {
    if (client.getPosY() < modules.getSlider("Height") && client.getMotionY() < 0) {
        client.setMotion(0, 0.5, 0);
        client.setOnGround(true);
    }
}
