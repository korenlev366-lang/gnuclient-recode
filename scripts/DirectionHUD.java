public void onOverlay(Object sr) {
    if (sr instanceof net.minecraft.client.gui.ScaledResolution) {
        net.minecraft.client.gui.FontRenderer fr = Mc.fontRenderer();
        float yaw = client.getYaw();
        String dir;
        if (yaw < 0) yaw += 360;
        if (yaw >= 315 || yaw < 45) dir = "South (+Z)";
        else if (yaw >= 45 && yaw < 135) dir = "West (-X)";
        else if (yaw >= 135 && yaw < 225) dir = "North (-Z)";
        else dir = "East (+X)";
        fr.drawStringWithShadow("\u00a77Facing: \u00a7f" + dir, 4, 104, 0xFFFFFF);
    }
}
