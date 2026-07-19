void onPreUpdate() {
    if (!keybinds.isMouseDown(0)) return;
    Object hit = client.raycastBlock(5.0f, client.getYaw(), client.getPitch());
    if (!client.raycastHitBlock(hit)) return;
    Object block = world.getBlockFromHit(hit);
    int best = inventory.findBestHotbarTool(block);
    if (best >= 0 && inventory.getSlot() != best) {
        inventory.setSlot(best);
    }
}
