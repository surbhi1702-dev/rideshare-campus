import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { ErrorAlert } from '../components/Feedback.jsx';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      navigate(location.state?.from || '/', { replace: true });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main>
      <div className="narrow">
        <h1>Share the cab, split the fare</h1>
        <p className="muted">Find students from your institute going the same way at the same time.</p>
        <form className="panel" onSubmit={submit}>
          <ErrorAlert error={error} />
          <div className="field">
            <label htmlFor="email">Institute email</label>
            <input id="email" type="email" autoComplete="username" value={email}
                   onChange={(e) => setEmail(e.target.value)} required />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input id="password" type="password" autoComplete="current-password" value={password}
                   onChange={(e) => setPassword(e.target.value)} required />
          </div>
          <button className="btn" type="submit" disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</button>
        </form>
        <p>New here? <Link to="/register">Create an account</Link></p>
      </div>
    </main>
  );
}
