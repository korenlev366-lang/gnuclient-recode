void onPreUpdate() {
    if (keybinds.isForwardDown() && client.isOnGround()
            && !client.isSneaking() && !status.isBlocking()) {
        if (!client.isServerSprinting()) {
            client.sendSprintStart();
        }
        client.setSprinting(true);
    }
}
