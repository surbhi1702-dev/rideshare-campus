import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Login from './Login.jsx';
import { ApiRequestError } from '../api/client.js';

// A plain function rather than vi.fn(): vitest 2.1's mock tracks returned
// promises and reports a rejection as unhandled even when the component catches it.
let loginImpl;
const loginCalls = [];
vi.mock('../auth/AuthContext.jsx', () => ({
  useAuth: () => ({ login: (...args) => { loginCalls.push(args); return loginImpl(...args); } }),
}));

function renderLogin() {
  return render(<MemoryRouter><Login /></MemoryRouter>);
}

describe('Login page', () => {
  beforeEach(() => {
    loginCalls.length = 0;
    loginImpl = async () => {};
  });

  it('logs in with the typed credentials', async () => {
    renderLogin();

    await userEvent.type(screen.getByLabelText('Institute email'), 'aarav@iitbbs.ac.in');
    await userEvent.type(screen.getByLabelText('Password'), 'Password123');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(loginCalls).toEqual([['aarav@iitbbs.ac.in', 'Password123']]);
  });

  it('shows the server error and re-enables the button', async () => {
    loginImpl = async () => {
      throw new ApiRequestError(401, { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' });
    };
    renderLogin();

    await userEvent.type(screen.getByLabelText('Institute email'), 'aarav@iitbbs.ac.in');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
    expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled();
  });

  it('explains rate limiting in plain words', async () => {
    const error = new ApiRequestError(429, { code: 'RATE_LIMITED' });
    error.retryAfter = 30;
    loginImpl = async () => {
      throw error;
    };
    renderLogin();

    await userEvent.type(screen.getByLabelText('Institute email'), 'aarav@iitbbs.ac.in');
    await userEvent.type(screen.getByLabelText('Password'), 'whatever1');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('wait 30 seconds');
  });
});
