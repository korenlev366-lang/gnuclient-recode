void onPreUpdate() {
    Object hit = client.raycastBlock(5.0f, client.getYaw(), client.getPitch());
    if (hit != null && keybinds.isMouseDown(0)) {
        // Block state via world.getBlockAt(); tool compare needs inventory scanning
    }
}
