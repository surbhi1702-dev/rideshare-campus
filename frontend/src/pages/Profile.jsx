import { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { ErrorAlert, SuccessAlert } from '../components/Feedback.jsx';

export default function Profile() {
  const { user, setUser } = useAuth();
  const [profile, setProfile] = useState({ name: user.name, phoneNumber: user.phoneNumber || '' });
  const [passwords, setPasswords] = useState({ currentPassword: '', newPassword: '' });
  const [blocked, setBlocked] = useState([]);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    api.blocks().then(setBlocked).catch(setError);
  }, []);

  const wrap = (fn) => async (event) => {
    event?.preventDefault();
    setError(null);
    setNotice('');
    try {
      await fn();
    } catch (e) {
      setError(e);
    }
  };

  const saveProfile = wrap(async () => {
    const updated = await api.updateMe({ name: profile.name, phoneNumber: profile.phoneNumber || null });
    setUser(updated);
    setNotice('Profile saved.');
  });

  const changePassword = wrap(async () => {
    await api.changePassword(passwords);
    setPasswords({ currentPassword: '', newPassword: '' });
    setNotice('Password changed.');
  });

  const unblock = (person) => wrap(async () => {
    await api.unblock(person.userId);
    setBlocked(blocked.filter((b) => b.userId !== person.userId));
    setNotice(`${person.displayName} is unblocked.`);
  });

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Profile</h1>
          <p>{user.email}</p>
        </div>
      </div>
      <ErrorAlert error={error} />
      <SuccessAlert>{notice}</SuccessAlert>
      <div className="grid-2">
        <form className="panel" onSubmit={saveProfile}>
          <h2>Your details</h2>
          <div className="field">
            <label htmlFor="p-name">Full name</label>
            <input id="p-name" value={profile.name} onChange={(e) => setProfile({ ...profile, name: e.target.value })}
                   required />
            <span className="hint">Others outside your ride only see your first name and initial.</span>
          </div>
          <div className="field">
            <label htmlFor="p-phone">Phone number</label>
            <input id="p-phone" type="tel" value={profile.phoneNumber}
                   onChange={(e) => setProfile({ ...profile, phoneNumber: e.target.value })} />
            <span className="hint">Shared only with members of rides you are in.</span>
          </div>
          <button type="submit" className="btn">Save profile</button>
        </form>

        <form className="panel" onSubmit={changePassword}>
          <h2>Change password</h2>
          <div className="field">
            <label htmlFor="p-current">Current password</label>
            <input id="p-current" type="password" autoComplete="current-password" value={passwords.currentPassword}
                   onChange={(e) => setPasswords({ ...passwords, currentPassword: e.target.value })} required />
          </div>
          <div className="field">
            <label htmlFor="p-new">New password</label>
            <input id="p-new" type="password" autoComplete="new-password" minLength={8} value={passwords.newPassword}
                   onChange={(e) => setPasswords({ ...passwords, newPassword: e.target.value })} required />
          </div>
          <button type="submit" className="btn">Change password</button>
        </form>
      </div>

      <section className="panel">
        <h2>Blocked students</h2>
        {blocked.length === 0 ? (
          <p className="muted">You haven't blocked anyone. You can block someone from a ride's group list.</p>
        ) : (
          <table>
            <tbody>
              {blocked.map((b) => (
                <tr key={b.userId}>
                  <td>{b.displayName}</td>
                  <td className="num">
                    <button type="button" className="btn secondary small" onClick={unblock(b)}>Unblock</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
