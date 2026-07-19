void onPreUpdate() {
    if (!client.isOnGround() && keybinds.isMovementDown()) {
        float yaw = client.getYaw();
        float forward = keybinds.isForwardDown() ? 0.5f : 0.0f;
        float strafe = 0.0f;
        if (keybinds.isLeftDown()) strafe = 0.5f;
        if (keybinds.isRightDown()) strafe = -0.5f;
        if (keybinds.isBackDown()) forward = -0.5f;

        float rad = (float) Math.toRadians(yaw);
        double mx = (-Math.sin(rad) * forward + Math.cos(rad) * strafe) * 0.2873;
        double mz = (Math.cos(rad) * forward + Math.sin(rad) * strafe) * 0.2873;
        client.setMotion(mx, client.getMotionY(), mz);
    }
}
