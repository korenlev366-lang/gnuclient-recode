void onLoad() {
    modules.registerButton("Toast", true);
    modules.registerButton("Chat", true);
}

void onAttack(Object entity) {
    if (!(entity instanceof net.minecraft.entity.Entity)) return;
    String name = ((net.minecraft.entity.Entity) entity).getName();
    shared.put("lastAttackTarget", name);
    shared.emit("combat", "attack:" + name);
    if (modules.getButton("Toast")) {
        hud.notify("Hit " + name, true);
    }
    if (modules.getButton("Chat")) {
        Mc.addChatMessage("\u00a7e[AttackHook] " + name
            + " ht=" + status.getHurtTime(entity)
            + " hp=" + status.getHealth(entity));
    }
}
