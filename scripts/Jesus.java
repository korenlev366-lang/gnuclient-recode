void onPreUpdate() {
    if (status.isInWater()) {
        client.setMotion(client.getMotionX(), 0.08, client.getMotionZ());
        client.setOnGround(true);
    }
}
