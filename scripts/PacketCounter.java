int sentCount = 0;
int recvCount = 0;
int lastSecondSent = 0;
int lastSecondRecv = 0;
long lastCountTime = 0;

boolean onPacketSend(Object packet) {
    sentCount++;
    return false;
}

boolean onPacketReceive(Object packet) {
    recvCount++;
    return false;
}

void onPreUpdate() {
    long now = client.time();
    if (now - lastCountTime >= 1000) {
        lastSecondSent = sentCount;
        lastSecondRecv = recvCount;
        sentCount = 0;
        recvCount = 0;
        lastCountTime = now;
    }
}

public void onOverlay(Object sr) {
    if (sr instanceof net.minecraft.client.gui.ScaledResolution) {
        net.minecraft.client.gui.FontRenderer fr = Mc.fontRenderer();
        fr.drawStringWithShadow("\u00a77Sent: \u00a7f" + lastSecondSent + "/s  \u00a77Recv: \u00a7f" + lastSecondRecv + "/s",
            4, 88, 0xFFFFFF);
    }
}
