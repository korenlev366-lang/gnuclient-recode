public void onOverlay(Object sr) {
    if (sr instanceof net.minecraft.client.gui.ScaledResolution) {
        net.minecraft.client.gui.FontRenderer fr = Mc.fontRenderer();
        float hp = status.getHealth();
        float max = status.getMaxHealth();
        float abs = status.getAbsorption();
        String hpStr = String.format("%.1f", hp + abs);
        String maxStr = String.format("%.1f", max);
        String color = (hp + abs > max * 0.5f) ? "\u00a7a" : (hp + abs > max * 0.25f ? "\u00a7e" : "\u00a7c");
        fr.drawStringWithShadow(color + "HP: \u00a7f" + hpStr + "/" + maxStr, 4, 56, 0xFFFFFF);
    }
}
