export function formatDateTime(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }

  const date = typeof value === 'number' ? new Date(value) : new Date(String(value));
  if (Number.isNaN(date.getTime())) {
    return '-';
  }

  return date.toLocaleString();
}

export function formatRelativeAge(timestamp?: number | null): string {
  if (!timestamp) {
    return 'unknown';
  }

  const seconds = Math.max(0, Math.floor((Date.now() - timestamp) / 1000));
  if (seconds < 10) {
    return 'just now';
  }
  if (seconds < 60) {
    return `${seconds}s ago`;
  }

  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes}m ago`;
  }

  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours}h ago`;
  }

  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

export function formatHours(hours?: number | null): string {
  if (hours === null || hours === undefined) {
    return '-';
  }

  if (hours < 24) {
    return `${hours} h`;
  }

  const days = Math.floor(hours / 24);
  const remainHours = hours % 24;
  return remainHours === 0 ? `${days} d` : `${days} d ${remainHours} h`;
}

export function formatBrokerAddress(ip?: string, tcpPort?: number, httpPort?: number): string {
  if (!ip) {
    return '-';
  }

  const segments: string[] = [];
  if (tcpPort) {
    segments.push(`TCP ${ip}:${tcpPort}`);
  }
  if (httpPort) {
    segments.push(`HTTP ${ip}:${httpPort}`);
  }

  return segments.length > 0 ? segments.join(' / ') : ip;
}

