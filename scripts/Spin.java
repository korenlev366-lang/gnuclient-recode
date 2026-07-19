void onLoad() {
    modules.registerSlider("Speed", 5.0f, 1.0f, 50.0f);
}

void onPreUpdate() {
    float yaw = client.getYaw() + modules.getSlider("Speed");
    client.setRotation(yaw, client.getPitch());
}
