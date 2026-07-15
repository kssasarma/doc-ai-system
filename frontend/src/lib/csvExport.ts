/** Quotes/escapes a single CSV field per RFC 4180 — wraps in quotes only when needed. */
function escapeCsvField(value: unknown): string {
  const s = value == null ? '' : String(value);
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

/** Downloads `rows` as a CSV file — used by the analytics tabs' "Export CSV" buttons (Phase 6.5).
 * `columns` controls both the header row and the key order (object key order isn't guaranteed). */
export function downloadCsv<T extends object>(filename: string, rows: T[], columns: (keyof T)[]): void {
  const header = columns.map(c => escapeCsvField(String(c))).join(',');
  const body = rows.map(row => columns.map(c => escapeCsvField(row[c])).join(',')).join('\n');
  const blob = new Blob([header + '\n' + body], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
