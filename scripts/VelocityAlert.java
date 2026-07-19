void onLoad() {
    modules.registerButton("Notify", true);
}

void onPreUpdate() {
    lenience.decayTick();
    if (modules.getButton("Notify") && lenience.getKbWindow() > 0) {
        Mc.addChatMessage("\u00a7c[VelocityAlert] Knockback received!");
    }
}
