public void onOverlay(Object sr) {
    if (sr instanceof net.minecraft.client.gui.ScaledResolution) {
        net.minecraft.client.gui.FontRenderer fr = Mc.fontRenderer();
        String x = String.format("%.1f", client.getPosX());
        String y = String.format("%.1f", client.getPosY());
        String z = String.format("%.1f", client.getPosZ());
        fr.drawStringWithShadow("\u00a77X: \u00a7f" + x, 4, 4, 0xFFFFFF);
        fr.drawStringWithShadow("\u00a77Y: \u00a7f" + y, 4, 16, 0xFFFFFF);
        fr.drawStringWithShadow("\u00a77Z: \u00a7f" + z, 4, 28, 0xFFFFFF);
    }
}
