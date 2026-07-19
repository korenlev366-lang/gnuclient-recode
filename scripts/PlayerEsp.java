void onLoad() {
    modules.registerSlider("Range", 64.0f, 8.0f, 128.0f);
    modules.registerButton("Tracers", true);
    modules.registerButton("Boxes", true);
}

public void onRender(float partialTicks) {
    draw.beginWorld();
    java.util.List<?> entities = world.getEntities();
    double range = modules.getSlider("Range");
    for (int i = 0; i < entities.size(); i++) {
        Object e = entities.get(i);
        if (!world.isPlayer(e) || e == client.getPlayer()) continue;
        if (world.distanceTo(e) > range) continue;
        if (modules.getButton("Boxes")) {
            draw.entityBox(e, partialTicks, 1.0f, 0.25f, 0.25f, 0.2f);
        }
        if (modules.getButton("Tracers")) {
            draw.tracer(e, partialTicks, 1.0f, 0.4f, 0.4f, 0.85f, 1.5f);
        }
    }
    draw.endWorld();
}

public void onOverlay(Object sr) {
    draw.text("\u00a7cPlayerESP", 4, 4, 0xFFFFFF);
}
