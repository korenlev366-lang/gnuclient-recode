public void onOverlay(Object sr) {
    if (sr instanceof net.minecraft.client.gui.ScaledResolution) {
        net.minecraft.client.gui.FontRenderer fr = Mc.fontRenderer();
        double mx = client.getMotionX();
        double mz = client.getMotionZ();
        double speed = Math.sqrt(mx * mx + mz * mz) * 20.0;
        String speedStr = String.format("%.1f", speed);
        fr.drawStringWithShadow("\u00a7bSpeed: \u00a7f" + speedStr + " b/s", 4, 40, 0xFFFFFF);
    }
}
