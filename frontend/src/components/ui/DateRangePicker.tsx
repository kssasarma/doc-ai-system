import { cn } from '../../lib/cn';

const PRESETS = [
  { label: '7d', days: 7 },
  { label: '30d', days: 30 },
  { label: '90d', days: 90 },
];

interface DateRangePickerProps {
  days: number | null;
  onChange: (days: number | null) => void;
  /** Adds an "All time" pill mapping to `null` — for views (e.g. the audit log) where showing
   * everything by default matters more than defaulting to a recent window. */
  allowAll?: boolean;
}

/** Preset-only date-range control (see the dataviz skill's interaction.md — "date range first,
 * presets before a custom range"). Custom ranges aren't supported here since the analytics
 * endpoints only take a `days` lookback, not arbitrary from/to bounds — a deliberate scope trim. */
export default function DateRangePicker({ days, onChange, allowAll }: DateRangePickerProps) {
  const presets = allowAll ? [{ label: 'All', days: null }, ...PRESETS] : PRESETS;
  return (
    <div className="inline-flex rounded-lg border border-border bg-surface p-0.5" role="group" aria-label="Date range">
      {presets.map(p => (
        <button
          key={p.label}
          type="button"
          onClick={() => onChange(p.days)}
          aria-pressed={days === p.days}
          className={cn(
            'px-3 py-1 text-xs font-medium rounded-md transition-colors',
            days === p.days ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
          )}
        >
          {p.label}
        </button>
      ))}
    </div>
  );
}
