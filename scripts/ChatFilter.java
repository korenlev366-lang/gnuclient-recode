void onLoad() {
    modules.registerButton("FilterAds", true);
    modules.registerButton("FilterParty", false);
}

boolean onPacketReceive(Object packet) {
    if (!packets.isChatReceive(packet)) return false;
    String text = packets.chatText(packet);
    if (text == null) return false;
    String lower = text.toLowerCase();
    if (modules.getButton("FilterAds")) {
        if (lower.contains("buy now") || lower.contains("discord.gg")
                || lower.contains("cheap ranks") || lower.contains("[ad]")) {
            return true;
        }
    }
    if (modules.getButton("FilterParty")) {
        if (lower.contains("has invited you to join their party")
                || lower.contains("party request")) {
            return true;
        }
    }
    return false;
}
