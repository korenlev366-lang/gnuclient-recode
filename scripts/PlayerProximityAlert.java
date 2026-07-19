void onLoad() {
    modules.registerSlider("Range", 10.0f, 3.0f, 30.0f);
}

java.util.Set<String> alerted = new java.util.HashSet<String>();

void onPreUpdate() {
    java.util.List<?> entities = world.getEntities();
    java.util.Set<String> current = new java.util.HashSet<String>();
    double range = modules.getSlider("Range");
    for (int i = 0; i < entities.size(); i++) {
        Object e = entities.get(i);
        if (world.isPlayer(e) && e != client.getPlayer()) {
            double dist = world.distanceTo(e);
            if (dist < range) {
                if (!(e instanceof net.minecraft.entity.Entity)) continue;
                String name = ((net.minecraft.entity.Entity) e).getName();
                current.add(name);
                if (!alerted.contains(name)) {
                    Mc.addChatMessage("\u00a7c[Proximity] " + name + " is " + String.format("%.1f", dist) + " blocks away!");
                }
            }
        }
    }
    alerted = current;
}
