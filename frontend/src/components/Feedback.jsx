/** Shown while data loads, so a slow network never looks like an empty page. */
export function Loading({ label = 'Loading…' }) {
  return (
    <div className="loading" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function ErrorAlert({ error, onRetry }) {
  if (!error) return null;
  return (
    <div className="alert error" role="alert">
      {friendlyMessage(error)}
      {onRetry && (
        <button type="button" className="btn secondary small alert-action" onClick={onRetry}>Try again</button>
      )}
      {error.fieldErrors?.length > 0 && (
        <ul>
          {error.fieldErrors.map((f) => (
            <li key={f.field + f.message}>
              {humanize(f.field)} {f.message}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function SuccessAlert({ children }) {
  if (!children) return null;
  return <div className="alert success" role="status">{children}</div>;
}

export function Empty({ children, action }) {
  return (
    <div className="empty">
      <p>{children}</p>
      {action}
    </div>
  );
}

export function Pager({ page, onChange }) {
  if (!page || page.totalPages <= 1) return null;
  return (
    <div className="pager">
      <button type="button" className="btn secondary small" disabled={page.page === 0}
              onClick={() => onChange(page.page - 1)}>Previous</button>
      <span className="muted small">Page {page.page + 1} of {page.totalPages}</span>
      <button type="button" className="btn secondary small" disabled={page.page + 1 >= page.totalPages}
              onClick={() => onChange(page.page + 1)}>Next</button>
    </div>
  );
}

function friendlyMessage(error) {
  if (error.code === 'RATE_LIMITED') {
    return error.retryAfter
      ? `Too many attempts. Please wait ${error.retryAfter} seconds and try again.`
      : 'Too many attempts. Please wait a moment and try again.';
  }
  return error.message;
}

function humanize(field) {
  const words = field.replace(/([A-Z])/g, ' $1').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}
