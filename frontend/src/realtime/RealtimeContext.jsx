import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { api, getAccessToken, refreshSession } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';

/**
 * One STOMP-over-WebSocket connection per signed-in tab.
 *  - /user/queue/notifications : my new notifications (unread badge + toast)
 *  - /topic/rides/{id}         : seat/status changes of a ride I'm looking at
 * The socket is push-only; every action still goes through REST.
 */
const RealtimeContext = createContext(null);

function brokerUrl() {
  // When the frontend host cannot proxy WebSockets (e.g. Vercel), point straight at the API.
  if (import.meta.env.VITE_WS_URL) return import.meta.env.VITE_WS_URL;
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws`;
}

/** True if the JWT expires within the next 30 seconds (or cannot be read). */
function expiresSoon(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return payload.exp * 1000 - Date.now() < 30_000;
  } catch {
    return true;
  }
}

export function RealtimeProvider({ children }) {
  const { user } = useAuth();
  const userId = user?.id;
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
    if (!userId) {
      setUnread(0);
      return undefined;
    }
    refreshUnread();

    const client = new Client({
      brokerURL: brokerUrl(),
      reconnectDelay: 5000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
    });
    // Access tokens are short-lived: (re)connects always use a fresh one.
    client.beforeConnect = async () => {
      if (expiresSoon(getAccessToken())) {
        try {
          await refreshSession();
        } catch {
          /* REST calls will sign the user out */
        }
      }
      client.connectHeaders = { Authorization: `Bearer ${getAccessToken()}` };
    };
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
  }, [userId, refreshUnread, attach]);

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
