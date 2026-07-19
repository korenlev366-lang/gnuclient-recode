void onLoad() {
    commands.register("ping");
    commands.register("bus");
}

void onShared(String channel, Object payload) {
    if ("combat".equals(channel)) {
        hud.notify("bus:" + String.valueOf(payload), true);
    }
}

String onCommand(String name, String[] args) {
    if ("ping".equals(name)) {
        shared.put("lastPing", Long.valueOf(client.time()));
        shared.emit("chat", "ping");
        return "pong (shared lastPing=" + shared.getInt("lastPing", 0) + ")";
    }
    if ("bus".equals(name)) {
        String msg = args.length > 0 ? args[0] : "hello";
        shared.emit("combat", msg);
        return "emitted to combat: " + msg;
    }
    return null;
}
