void onLoad() {
    modules.registerSlider("MinHurtTime", 8, 0, 10);
}

void onPreUpdate() {
    java.util.List<?> entities = world.getEntities();
    for (int i = 0; i < entities.size(); i++) {
        Object e = entities.get(i);
        if (world.isPlayer(e) && status.getHurtTime(e) < modules.getSlider("MinHurtTime")) {
            // Entity is out of hurtTime — safe to attack again
        }
    }
}
