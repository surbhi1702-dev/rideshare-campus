// Thin fetch wrapper: adds the access token, parses JSON and turns error bodies
// (the backend's ApiError contract) into ApiRequestError instances.
//
// Sessions (V2):
//  - The short-lived access token lives only in memory (this module), never in
//    localStorage, so an XSS bug cannot read a long-lived credential.
//  - The refresh token is an httpOnly cookie the browser sends to /api/auth only.
//  - A 401 on a normal call triggers one silent refresh (shared by all callers that
//    failed at the same time) and a single retry.

const API_BASE = `${import.meta.env.VITE_API_URL || ''}/api`;
const LEGACY_TOKEN_KEY = 'rideshare.token';

let accessToken = null;
let onSessionExpired = () => {};
let refreshInFlight = null;

// V1 kept the token in localStorage; remove it once so it can't be reused.
try {
  localStorage.removeItem(LEGACY_TOKEN_KEY);
} catch {
  /* storage unavailable */
}

export function setSessionExpiredHandler(handler) {
  onSessionExpired = handler;
}

export function getAccessToken() {
  return accessToken;
}

export function setAccessToken(token) {
  accessToken = token || null;
}

export class ApiRequestError extends Error {
  constructor(status, body) {
    super(body?.message || `Request failed (${status})`);
    this.status = status;
    this.code = body?.code || 'UNKNOWN';
    this.fieldErrors = body?.fieldErrors || [];
    this.retryAfter = body?.retryAfter;
  }
}

function buildQuery(params) {
  if (!params) return '';
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.append(key, value);
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

async function send(path, { method = 'GET', body, params, headers: extraHeaders } = {}) {
  const headers = { Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest', ...extraHeaders };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response;
  try {
    response = await fetch(`${API_BASE}${path}${buildQuery(params)}`, {
      method,
      headers,
      credentials: 'include',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiRequestError(0, { message: 'Cannot reach the server. Check your connection and try again.' });
  }

  if (response.status === 204) return null;
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!response.ok) {
    const error = new ApiRequestError(response.status, data);
    const retryAfter = response.headers?.get?.('Retry-After');
    if (retryAfter) error.retryAfter = Number(retryAfter);
    throw error;
  }
  return data;
}

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers share
 * one request, so a page firing five calls with an expired token refreshes once.
 * If two tabs refreshed with the same cookie at the same instant, the loser gets a
 * 401 but the browser already holds the winner's new cookie: retry once.
 */
export function refreshSession() {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        return await send('/auth/refresh', { method: 'POST' });
      } catch (error) {
        if (error.status !== 401) throw error;
        await new Promise((resolve) => setTimeout(resolve, 300));
        return send('/auth/refresh', { method: 'POST' });
      }
    })()
      .then((auth) => {
        setAccessToken(auth.accessToken);
        return auth;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

const NO_REFRESH_PATHS = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'];

export async function request(path, options = {}) {
  try {
    return await send(path, options);
  } catch (error) {
    if (error.status !== 401 || NO_REFRESH_PATHS.includes(path)) throw error;
    try {
      await refreshSession();
    } catch {
      setAccessToken(null);
      onSessionExpired();
      throw error;
    }
    return send(path, options);
  }
}

function newIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

/**
 * For "join" style requests: one key per button press, reused for retries. If the
 * network drops after the server booked the seat, the retry gets the same result
 * instead of a second booking or a confusing "already joined".
 */
export async function requestWithRetry(path, options, { attempts = 3, delayMs = 600 } = {}) {
  const key = newIdempotencyKey();
  const withKey = { ...options, headers: { ...options.headers, 'Idempotency-Key': key } };
  for (let attempt = 1; ; attempt += 1) {
    try {
      return await request(path, withKey);
    } catch (error) {
      const transient = error.status === 0 || error.status >= 502;
      if (!transient || attempt >= attempts) throw error;
      await new Promise((resolve) => setTimeout(resolve, delayMs * attempt));
    }
  }
}

// ------------------------------------------------------------------ endpoints

export const api = {
  register: (body) => request('/auth/register', { method: 'POST', body }),
  login: (body) => request('/auth/login', { method: 'POST', body }),
  logout: () => request('/auth/logout', { method: 'POST' }),

  me: () => request('/users/me'),
  updateMe: (body) => request('/users/me', { method: 'PUT', body }),
  changePassword: (body) => request('/users/me/password', { method: 'PUT', body }),

  places: () => request('/places'),

  createRide: (body) => request('/rides', { method: 'POST', body }),
  updateRide: (id, body) => request(`/rides/${id}`, { method: 'PUT', body }),
  browseRides: (params) => request('/rides', { params }),
  myRides: (params) => request('/rides/mine', { params }),
  ride: (id) => request(`/rides/${id}`),
  searchRides: (params) => request('/rides/search', { params }),
  matchesForRide: (id) => request(`/rides/${id}/matches`),
  joinRide: (id, body = { seats: 1 }) => requestWithRetry(`/rides/${id}/join`, { method: 'POST', body }),
  leaveRide: (id) => request(`/rides/${id}/leave`, { method: 'POST' }),
  joinWaitlist: (id, body = { seats: 1 }) => requestWithRetry(`/rides/${id}/waitlist`, { method: 'POST', body }),
  leaveWaitlist: (id) => request(`/rides/${id}/waitlist`, { method: 'DELETE' }),
  cancelRide: (id) => request(`/rides/${id}/cancel`, { method: 'POST' }),
  startRide: (id) => request(`/rides/${id}/start`, { method: 'POST' }),
  completeRide: (id) => request(`/rides/${id}/complete`, { method: 'POST' }),

  notifications: (params) => request('/notifications', { params }),
  unreadCount: () => request('/notifications/unread-count'),
  markRead: (id) => request(`/notifications/${id}/read`, { method: 'PATCH' }),
  markAllRead: () => request('/notifications/read-all', { method: 'PATCH' }),

  blocks: () => request('/users/me/blocks'),
  block: (userId) => request(`/users/me/blocks/${userId}`, { method: 'POST' }),
  unblock: (userId) => request(`/users/me/blocks/${userId}`, { method: 'DELETE' }),
  report: (body) => request('/reports', { method: 'POST', body }),

  adminStats: () => request('/admin/stats'),
  adminUsers: (params) => request('/admin/users', { params }),
  adminSetActive: (id, active) =>
    request(`/admin/users/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'PATCH' }),
  adminRides: (params) => request('/admin/rides', { params }),
  adminReports: (params) => request('/admin/reports', { params }),
  adminUpdateReport: (id, status) => request(`/admin/reports/${id}`, { method: 'PATCH', body: { status } }),
};
