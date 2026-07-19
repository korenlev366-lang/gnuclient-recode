void onLoad() {
    modules.registerButton("Notify", true);
}

boolean onPacketReceive(Object packet) {
    if (packets.isSelfVelocity(packet)) {
        lenience.bumpKbWindow(10);
        if (modules.getButton("Notify")) {
            Mc.addChatMessage("\u00a7c[VelocityAlert] Knockback received!");
        }
    }
    return false;
}

void onPreUpdate() {
    lenience.decayTick();
}
