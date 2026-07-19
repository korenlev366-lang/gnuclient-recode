void patchMovementInput(Object movInput) {
    if (!client.hasScreen()) return;
    float forward = 0.0f;
    float strafe = 0.0f;
    if (keybinds.isForwardDown()) forward++;
    if (keybinds.isBackDown()) forward--;
    if (keybinds.isLeftDown()) strafe++;
    if (keybinds.isRightDown()) strafe--;
    if (forward != 0.0f || strafe != 0.0f) {
        float len = (float) Math.sqrt(forward * forward + strafe * strafe);
        forward /= len;
        strafe /= len;
    }
    client.setMovementInput(movInput, forward, strafe, keybinds.isJumpDown());
}
