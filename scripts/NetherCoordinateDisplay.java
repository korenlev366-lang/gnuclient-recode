public void onOverlay(Object sr) {
    if (sr instanceof net.minecraft.client.gui.ScaledResolution) {
        net.minecraft.client.gui.FontRenderer fr = Mc.fontRenderer();
        double ox = client.getPosX();
        double oz = client.getPosZ();
        double nx = ox / 8.0;
        double nz = oz / 8.0;
        fr.drawStringWithShadow("\u00a7aOverworld: \u00a7f" + String.format("%.0f", ox) + ", " + String.format("%.0f", oz), 4, 136, 0xFFFFFF);
        fr.drawStringWithShadow("\u00a7cNether: \u00a7f" + String.format("%.0f", nx) + ", " + String.format("%.0f", nz), 4, 148, 0xFFFFFF);
    }
}
