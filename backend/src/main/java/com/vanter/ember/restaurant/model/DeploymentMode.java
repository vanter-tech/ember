package com.vanter.ember.restaurant.model;

/** Where a restaurant runs: on Ember Web (cloud) or on an on-premise Ember Hub. */
public enum DeploymentMode {
    CLOUD, HUB;

    /**
     * True when the cloud must refuse this restaurant: it runs on a Hub, so nobody may use Ember
     * Web for it. {@code enforced} is false inside the Hub itself ({@code
     * ember.deployment-mode.enforced}), where its own restaurant is HUB and must keep working.
     */
    public static boolean isClosedToWeb(Restaurant restaurant, boolean enforced) {
        return enforced && restaurant != null && restaurant.getDeploymentMode() == HUB;
    }
}
