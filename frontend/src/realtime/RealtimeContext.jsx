import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { api } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';

/**
 * One STOMP-over-WebSocket connection per signed-in tab.
 *  - /user/queue/notifications : my new notifications (unread badge + toast)
 *  - /topic/rides/{id}         : seat/status changes of a ride I'm looking at
 * The socket is push-only; every action still goes through REST.
 */
const RealtimeContext = createContext(null);

function brokerUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws`;
}

export function RealtimeProvider({ children }) {
  const { token } = useAuth();
  const clientRef = useRef(null);
  const listenersRef = useRef(new Map()); // destination -> Set<callback>
  const subscriptionsRef = useRef(new Map()); // callback -> active STOMP subscription
  const [connected, setConnected] = useState(false);
  const [unread, setUnread] = useState(0);
  const [toast, setToast] = useState(null);

  const attach = useCallback((client, destination, callback) => {
    const subscription = client.subscribe(destination, (frame) => callback(JSON.parse(frame.body)));
    subscriptionsRef.current.set(callback, subscription);
  }, []);

  const refreshUnread = useCallback(() => {
    api.unreadCount().then((r) => setUnread(r.unread)).catch(() => {});
  }, []);

  useEffect(() => {
    if (!token) {
      setUnread(0);
      return undefined;
    }
    refreshUnread();

    const client = new Client({
      brokerURL: brokerUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
    });
    client.onConnect = () => {
      setConnected(true);
      client.subscribe('/user/queue/notifications', (frame) => {
        const notification = JSON.parse(frame.body);
        setUnread((count) => count + 1);
        setToast(notification);
      });
      // Re-attach ride-topic listeners after (re)connects.
      listenersRef.current.forEach((callbacks, destination) => {
        callbacks.forEach((cb) => attach(client, destination, cb));
      });
      refreshUnread();
    };
    client.onWebSocketClose = () => setConnected(false);
    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
  }, [token, refreshUnread, attach]);

  useEffect(() => {
    if (!toast) return undefined;
    const timer = setTimeout(() => setToast(null), 6000);
    return () => clearTimeout(timer);
  }, [toast]);

  /** Subscribe to a ride's live updates; returns an unsubscribe function. */

  const subscribeToRide = useCallback((rideId, callback) => {
    const destination = `/topic/rides/${rideId}`;
    const callbacks = listenersRef.current.get(destination) || new Set();
    callbacks.add(callback);
    listenersRef.current.set(destination, callbacks);
    const client = clientRef.current;
    if (client?.connected) attach(client, destination, callback);
    return () => {
      callbacks.delete(callback);
      if (callbacks.size === 0) listenersRef.current.delete(destination);
      const subscription = subscriptionsRef.current.get(callback);
      subscriptionsRef.current.delete(callback);
      try {
        subscription?.unsubscribe();
      } catch {
        /* connection already closed */
      }
    };
  }, [attach]);

  const value = { connected, unread, setUnread, refreshUnread, subscribeToRide, toast, dismissToast: () => setToast(null) };
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}

export function useRealtime() {
  const context = useContext(RealtimeContext);
  if (!context) throw new Error('useRealtime must be used inside RealtimeProvider');
  return context;
}
