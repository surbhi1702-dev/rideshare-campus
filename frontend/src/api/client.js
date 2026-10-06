// Thin fetch wrapper: adds the JWT, parses JSON and turns error bodies
// (the backend's ApiError contract) into ApiRequestError instances.

const TOKEN_KEY = 'rideshare.token';

let onUnauthorized = () => {};

export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage unavailable (private mode): token lives only in memory for this tab */
  }
}

export class ApiRequestError extends Error {
  constructor(status, body) {
    super(body?.message || `Request failed (${status})`);
    this.status = status;
    this.code = body?.code || 'UNKNOWN';
    this.fieldErrors = body?.fieldErrors || [];
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

export async function request(path, { method = 'GET', body, params } = {}) {
  const headers = { Accept: 'application/json' };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response;
  try {
    response = await fetch(`/api${path}${buildQuery(params)}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiRequestError(0, { message: 'Cannot reach the server. Check your connection and try again.' });
  }

  if (response.status === 204) return null;
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    if (response.status === 401 && token) onUnauthorized();
    throw new ApiRequestError(response.status, data);
  }
  return data;
}

// ------------------------------------------------------------------ endpoints

export const api = {
  register: (body) => request('/auth/register', { method: 'POST', body }),
  login: (body) => request('/auth/login', { method: 'POST', body }),

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
  joinRide: (id, body = { seats: 1 }) => request(`/rides/${id}/join`, { method: 'POST', body }),
  leaveRide: (id) => request(`/rides/${id}/leave`, { method: 'POST' }),
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
