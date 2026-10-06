package com.rideshare.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Authenticates STOMP sessions with the same JWT as the REST API and restricts
 * what a client may subscribe to / send.
 * <ul>
 *   <li>CONNECT must carry "Authorization: Bearer &lt;token&gt;".</li>
 *   <li>SUBSCRIBE only to /user/queue/** (own queue) or /topic/rides/{id}.</li>
 *   <li>SEND is rejected: the socket is push-only.</li>
 * </ul>
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final TokenAuthenticator tokenAuthenticator;

    public StompAuthChannelInterceptor(TokenAuthenticator tokenAuthenticator) {
        this.tokenAuthenticator = tokenAuthenticator;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT -> {
                String header = accessor.getFirstNativeHeader("Authorization");
                Principal principal = tokenAuthenticator.authenticate(header)
                        .orElseThrow(() -> new MessageDeliveryException("Unauthenticated WebSocket connection"));
                accessor.setUser(principal);
            }
            case SUBSCRIBE -> {
                requireAuthenticated(accessor);
                String destination = accessor.getDestination();
                if (!isAllowedSubscription(destination)) {
                    throw new MessageDeliveryException("Subscription to " + destination + " is not allowed");
                }
            }
            case SEND -> throw new MessageDeliveryException("This socket is receive-only; use the REST API");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, heart-beats: nothing to check.
            }
        }
        return message;
    }

    static boolean isAllowedSubscription(String destination) {
        if (destination == null) {
            return false;
        }
        return destination.startsWith("/user/queue/")
                || destination.matches("^/topic/rides/\\d+$");
    }

    private static void requireAuthenticated(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("Unauthenticated WebSocket session");
        }
    }
}
