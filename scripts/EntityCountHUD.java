public void onOverlay(Object sr) {
    if (sr instanceof net.minecraft.client.gui.ScaledResolution) {
        net.minecraft.client.gui.FontRenderer fr = Mc.fontRenderer();
        int count = world.getEntities().size();
        fr.drawStringWithShadow("\u00a77Entities: \u00a7f" + count, 4, 120, 0xFFFFFF);
    }
}
