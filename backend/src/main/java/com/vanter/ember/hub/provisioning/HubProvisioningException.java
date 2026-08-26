package com.vanter.ember.hub.provisioning;

/** Anything that stops the Hub from seeding its local Restaurant/admin User the first time. */
public class HubProvisioningException extends RuntimeException {

    public HubProvisioningException(String message) {
        super(message);
    }

    public HubProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
