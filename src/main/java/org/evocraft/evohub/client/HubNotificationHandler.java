package org.evocraft.evohub.client;

/**
 * Legacy duplicate notification overlay consolidated into {@link JoinLeaveNotificationHandler}.
 * Kept as a stub without an event-bus subscriber to avoid duplicate chat suppression and overlay rendering.
 */
public class HubNotificationHandler {
    public enum NotifType { JOIN, LEAVE, TPA, TPA_HERE, DEATH, EVENT }
}
