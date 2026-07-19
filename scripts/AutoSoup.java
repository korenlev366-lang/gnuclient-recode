void onLoad() {
    modules.registerSlider("Health", 8.0f, 1.0f, 19.0f);
}

void onPreUpdate() {
    if (status.getHealth() > modules.getSlider("Health")) return;
    for (int i = 0; i < 9; i++) {
        Object stack = inventory.getStackInSlot(i);
        if (inventory.isSoup(stack)) {
            if (inventory.getSlot() != i) {
                inventory.setSlot(i);
            }
            keybinds.rightClick();
            break;
        }
    }
}
