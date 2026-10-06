import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, getAccessToken, request, setAccessToken, setSessionExpiredHandler } from './client.js';

function jsonResponse(status, body, headers = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => headers[name] ?? null },
    text: () => Promise.resolve(body === undefined ? '' : JSON.stringify(body)),
  };
}

describe('api client', () => {
  let fetchMock;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    setAccessToken(null);
    setSessionExpiredHandler(() => {});
  });

  it('sends the in-memory access token and the CSRF header, with cookies included', async () => {
    setAccessToken('abc');
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { id: 1 }));

    await request('/users/me');

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/users/me');
    expect(init.headers.Authorization).toBe('Bearer abc');
    expect(init.headers['X-Requested-With']).toBe('XMLHttpRequest');
    expect(init.credentials).toBe('include');
  });

  it('on 401 refreshes once and retries the original call with the new token', async () => {
    setAccessToken('expired');
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { code: 'AUTHENTICATION_REQUIRED' }))
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: 'fresh', user: { id: 1 } }))
      .mockResolvedValueOnce(jsonResponse(200, { id: 1 }));

    const result = await request('/users/me');

    expect(result).toEqual({ id: 1 });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/auth/refresh');
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe('Bearer fresh');
    expect(getAccessToken()).toBe('fresh');
  });

  it('shares one refresh between calls that fail at the same time', async () => {
    setAccessToken('expired');
    fetchMock.mockImplementation((url, init) => {
      if (url === '/api/auth/refresh') return Promise.resolve(jsonResponse(200, { accessToken: 'fresh' }));
      return Promise.resolve(init.headers.Authorization === 'Bearer fresh'
        ? jsonResponse(200, { ok: true })
        : jsonResponse(401, {}));
    });

    await Promise.all([request('/a'), request('/b'), request('/c')]);

    const refreshes = fetchMock.mock.calls.filter(([url]) => url === '/api/auth/refresh');
    expect(refreshes).toHaveLength(1);
  });

  it('signs the user out when the refresh fails', async () => {
    const expired = vi.fn();
    setSessionExpiredHandler(expired);
    setAccessToken('expired');
    fetchMock.mockResolvedValue(jsonResponse(401, { code: 'REFRESH_TOKEN_INVALID' }));

    await expect(request('/users/me')).rejects.toMatchObject({ status: 401 });
    expect(expired).toHaveBeenCalledOnce();
    expect(getAccessToken()).toBeNull();
  });

  it('does not try to refresh after a failed login', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' }));

    await expect(api.login({ email: 'a@iitbbs.ac.in', password: 'x' })).rejects.toMatchObject({
      code: 'INVALID_CREDENTIALS',
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('retries a join after a network drop with the same Idempotency-Key', async () => {
    vi.useFakeTimers();
    fetchMock
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(jsonResponse(200, { ride: { id: 5 } }));

    const promise = api.joinRide(5, { seats: 1 });
    await vi.runAllTimersAsync();
    await promise;
    vi.useRealTimers();

    expect(fetchMock).toHaveBeenCalledTimes(2);
    const firstKey = fetchMock.mock.calls[0][1].headers['Idempotency-Key'];
    expect(firstKey).toBeTruthy();
    expect(fetchMock.mock.calls[1][1].headers['Idempotency-Key']).toBe(firstKey);
  });

  it('does not retry a join the server rejected', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(409, { code: 'RIDE_FULL', message: 'Ride is full' }));

    await expect(api.joinRide(5)).rejects.toMatchObject({ code: 'RIDE_FULL' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('exposes Retry-After on rate-limited responses', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(429, { code: 'RATE_LIMITED' }, { 'Retry-After': '12' }));

    await expect(api.login({ email: 'a', password: 'b' })).rejects.toMatchObject({ code: 'RATE_LIMITED', retryAfter: 12 });
  });
});
