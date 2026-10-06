import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { ErrorAlert } from '../components/Feedback.jsx';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', password: '', phoneNumber: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const update = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await register({ ...form, phoneNumber: form.phoneNumber || undefined });
      navigate('/', { replace: true });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main>
      <div className="narrow">
        <h1>Create your account</h1>
        <p className="muted">Only institute email addresses can register.</p>
        <form className="panel" onSubmit={submit}>
          <ErrorAlert error={error} />
          <div className="field">
            <label htmlFor="name">Full name</label>
            <input id="name" autoComplete="name" value={form.name} onChange={update('name')} required />
          </div>
          <div className="field">
            <label htmlFor="email">Institute email</label>
            <input id="email" type="email" autoComplete="email" value={form.email} onChange={update('email')}
                   placeholder="yourname@iitbbs.ac.in" required />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input id="password" type="password" autoComplete="new-password" value={form.password}
                   onChange={update('password')} minLength={8} required />
            <span className="hint">At least 8 characters with a letter and a digit.</span>
          </div>
          <div className="field">
            <label htmlFor="phone">Phone number (optional)</label>
            <input id="phone" type="tel" autoComplete="tel" value={form.phoneNumber} onChange={update('phoneNumber')} />
            <span className="hint">Shown only to students in the same ride as you.</span>
          </div>
          <button className="btn" type="submit" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
        </form>
        <p>Already registered? <Link to="/login">Log in</Link></p>
      </div>
    </main>
  );
}
