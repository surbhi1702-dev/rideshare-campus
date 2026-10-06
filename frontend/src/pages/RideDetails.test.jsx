import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import RideDetails from './RideDetails.jsx';

const apiMock = vi.hoisted(() => ({ ride: vi.fn(), joinRide: vi.fn(), joinWaitlist: vi.fn(), leaveWaitlist: vi.fn() }));
vi.mock('../api/client.js', () => ({ api: apiMock }));
vi.mock('../auth/AuthContext.jsx', () => ({ useAuth: () => ({ user: { id: 9, name: 'Kabir' } }) }));
vi.mock('../realtime/RealtimeContext.jsx', () => ({ useRealtime: () => ({ subscribeToRide: () => () => {} }) }));

const NO_ACTIONS = {
  canJoin: false, canLeave: false, canEdit: false, canCancel: false, canStart: false, canComplete: false,
  canJoinWaitlist: false, canLeaveWaitlist: false,
};

function detail({ occupied = 4, actions = {}, waitlist = { size: 0 } } = {}) {
  return {
    ride: {
      id: 1, sourceName: 'Campus', destinationName: 'Airport', departureAt: '2030-10-07T09:00:00',
      totalSeats: 4, occupiedSeats: occupied, availableSeats: 4 - occupied, totalFare: 600, status: occupied === 4 ? 'FULL' : 'OPEN',
      creator: { id: 1, displayName: 'Aarav M.' },
    },
    viewerRole: null,
    participantCount: occupied,
    participants: [],
    estimatedShareIfJoined: occupied < 4 ? 150 : null,
    waitlist,
    actions: { ...NO_ACTIONS, ...actions },
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/rides/1']}>
      <Routes><Route path="/rides/:id" element={<RideDetails />} /></Routes>
    </MemoryRouter>,
  );
}

describe('Ride details', () => {
  beforeEach(() => Object.values(apiMock).forEach((fn) => fn.mockReset()));

  it('shows a loading state, then the ride', async () => {
    apiMock.ride.mockResolvedValue(detail({ occupied: 2, actions: { canJoin: true } }));
    renderPage();

    expect(screen.getByRole('status')).toHaveTextContent('Loading ride');
    expect(await screen.findByRole('button', { name: 'Join ride' })).toBeInTheDocument();
  });

  it('offers the waitlist on a full ride and shows the place in the queue after joining', async () => {
    apiMock.ride.mockResolvedValue(detail({ actions: { canJoinWaitlist: true }, waitlist: { size: 2 } }));
    apiMock.joinWaitlist.mockResolvedValue(detail({
      actions: { canLeaveWaitlist: true }, waitlist: { size: 3, viewerPosition: 3, viewerSeats: 1 },
    }));
    renderPage();

    expect(await screen.findByText(/2 students are waiting/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Join waitlist' }));

    expect(apiMock.joinWaitlist).toHaveBeenCalledWith('1', { seats: 1 });
    expect(await screen.findByText('#3')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Leave waitlist' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Join ride' })).not.toBeInTheDocument();
  });

  it('shows a retry button when the ride cannot be loaded', async () => {
    apiMock.ride.mockRejectedValueOnce({ message: 'Cannot reach the server.' })
      .mockResolvedValueOnce(detail({ occupied: 2, actions: { canJoin: true } }));
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('button', { name: 'Join ride' })).toBeInTheDocument();
  });
});
