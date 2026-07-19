void onLoad() {
    modules.registerSlider("Delay", 60, 20, 200);
}

int disconnectTimer = 0;
boolean wasInGame = false;

void onPreUpdate() {
    if (client.getPlayer() != null) {
        client.rememberServer();
        wasInGame = true;
        disconnectTimer = 0;
    } else if (wasInGame) {
        disconnectTimer++;
        if (disconnectTimer >= (int) modules.getSlider("Delay")) {
            Mc.addChatMessage("\u00a7a[AutoRejoin] Reconnecting to " + client.getLastServerIp() + "...");
            if (client.reconnectToLastServer()) {
                wasInGame = false;
            }
            disconnectTimer = 0;
        }
    }
}
