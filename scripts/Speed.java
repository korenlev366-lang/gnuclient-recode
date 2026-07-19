void onLoad() {
    modules.registerSlider("Speed", 1.2f, 1.0f, 2.0f);
}

int speedTick = 0;

void onPreUpdate() {
    if (!keybinds.isForwardDown()) { speedTick = 0; return; }
    float speed = modules.getSlider("Speed");
    float yaw = (float) Math.toRadians(client.getYaw());

    if (client.isOnGround()) {
        speedTick = 0;
        client.setMotion(-Math.sin(yaw) * 0.2873 * speed, 0.42, Math.cos(yaw) * 0.2873 * speed);
    } else {
        speedTick++;
        if (speedTick == 1) {
            client.setMotion(-Math.sin(yaw) * 0.2873 * speed * 1.1,
                -0.1, Math.cos(yaw) * 0.2873 * speed * 1.1);
        } else if (speedTick >= 2) {
            client.setMotion(-Math.sin(yaw) * 0.2873 * speed * 0.96,
                -0.2, Math.cos(yaw) * 0.2873 * speed * 0.96);
        }
    }
}
