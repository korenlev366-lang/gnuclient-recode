void onPreUpdate() {
    if (keybinds.isForwardDown() && client.isOnGround()) {
        client.setSprinting(true);
        if (!client.isServerSprinting()) {
            client.sendSprintStart();
        }
    }
}
