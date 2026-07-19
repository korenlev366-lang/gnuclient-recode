public void onOverlay(Object sr) {
    if (sr instanceof net.minecraft.client.gui.ScaledResolution) {
        net.minecraft.client.gui.FontRenderer fr = Mc.fontRenderer();
        float t = client.getTimerSpeed();
        String color = t > 1.01f ? "\u00a7c" : (t < 0.99f ? "\u00a7b" : "\u00a7a");
        fr.drawStringWithShadow(color + "Timer: \u00a7f" + String.format("%.1f", t) + "x", 4, 72, 0xFFFFFF);
    }
}
