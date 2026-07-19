void onPreUpdate() {
    if (keybinds.isForwardDown() && client.isOnGround()) {
        client.setJump(true);
    }
}
