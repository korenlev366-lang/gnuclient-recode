void onLoad() {
    modules.registerButton("DisableAuraWhenLow", true);
    modules.registerSlider("Health", 8.0f, 1.0f, 20.0f);
    commands.register("mod");
    commands.register("mods");
}

void onPreUpdate() {
    if (modules.getButton("DisableAuraWhenLow")
            && status.getHealth() <= modules.getSlider("Health")
            && modules.isEnabled("KillAura")) {
        modules.disable("KillAura");
        hud.notify("KillAura off (low HP)", false);
        shared.put("lastAuraDisable", Long.valueOf(client.time()));
        shared.emit("combat", "aura_disabled");
    }
}

String onCommand(String name, String[] args) {
    if ("mods".equals(name)) {
        java.util.List<String> names = modules.names();
        int on = 0;
        for (int i = 0; i < names.size(); i++) {
            if (modules.isEnabled(names.get(i))) on++;
        }
        return "modules: " + on + "/" + names.size() + " enabled";
    }
    if (!"mod".equals(name)) return null;
    if (args.length < 1) return "usage: .mod <name> [on|off|toggle]";
    String target = args[0];
    if (!modules.exists(target)) return "unknown module: " + target;
    if (args.length == 1 || (args.length > 1 && "toggle".equalsIgnoreCase(args[1]))) {
        modules.toggle(target);
    } else if ("on".equalsIgnoreCase(args[1]) || "enable".equalsIgnoreCase(args[1])) {
        modules.enable(target);
    } else if ("off".equalsIgnoreCase(args[1]) || "disable".equalsIgnoreCase(args[1])) {
        modules.disable(target);
    } else {
        return "usage: .mod <name> [on|off|toggle]";
    }
    return target + " => " + (modules.isEnabled(target) ? "ON" : "OFF");
}
