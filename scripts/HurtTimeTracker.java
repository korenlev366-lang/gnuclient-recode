void onLoad() {
    modules.registerSlider("MinHurtTime", 8, 0, 10);
    modules.registerSlider("Range", 6.0f, 3.0f, 12.0f);
    modules.registerButton("Notify", true);
}

java.util.Set<Integer> readyIds = new java.util.HashSet<Integer>();

void onPreUpdate() {
    java.util.List<?> entities = world.getEntities();
    java.util.Set<Integer> current = new java.util.HashSet<Integer>();
    float minHt = modules.getSlider("MinHurtTime");
    double range = modules.getSlider("Range");
    for (int i = 0; i < entities.size(); i++) {
        Object e = entities.get(i);
        if (!world.isPlayer(e) || e == client.getPlayer()) continue;
        if (world.distanceTo(e) > range) continue;
        if (!(e instanceof net.minecraft.entity.Entity)) continue;
        int id = Mc.entityId((net.minecraft.entity.Entity) e);
        if (status.getHurtTime(e) < minHt) {
            current.add(Integer.valueOf(id));
            if (modules.getButton("Notify") && !readyIds.contains(Integer.valueOf(id))) {
                String name = ((net.minecraft.entity.Entity) e).getName();
                Mc.addChatMessage("\u00a7a[HurtTime] " + name + " ready (ht=" + status.getHurtTime(e) + ")");
            }
        }
    }
    readyIds = current;
}
