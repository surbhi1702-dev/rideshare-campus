// Dates from the API are institute-local "YYYY-MM-DDTHH:mm[:ss]" strings without
// a zone. They are formatted from their parts so the browser's own time zone can
// never shift a departure time.

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function parts(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value || '');
  if (!match) return null;
  const [, y, m, d, hh, mm] = match;
  return { y: +y, m: +m, d: +d, hh, mm };
}

export function formatTime(value) {
  const p = parts(value);
  return p ? `${p.hh}:${p.mm}` : '';
}

export function formatDate(value) {
  const p = parts(value);
  if (!p) return '';
  const weekday = DAYS[new Date(Date.UTC(p.y, p.m - 1, p.d)).getUTCDay()];
  return `${weekday} ${p.d} ${MONTHS[p.m - 1]}`;
}

export function formatDateTime(value) {
  return `${formatDate(value)}, ${formatTime(value)}`;
}

export function formatMoney(value) {
  if (value === null || value === undefined) return '';
  const number = Number(value);
  return `₹${number.toLocaleString('en-IN', {
    minimumFractionDigits: Number.isInteger(number) ? 0 : 2,
    maximumFractionDigits: 2,
  })}`;
}

export function todayIso() {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export function statusLabel(status) {
  return {
    OPEN: 'Open',
    FULL: 'Full',
    STARTED: 'On the way',
    COMPLETED: 'Completed',
    CANCELLED: 'Cancelled',
  }[status] || status;
}
