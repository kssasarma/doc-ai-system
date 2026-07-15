interface ChartTooltipProps {
  active?: boolean;
  label?: string | number;
  /** Pre-formatted display strings — callers format (currency, %, etc.) before passing in. */
  items: { name: string; value: string; color: string }[];
}

/** Shared Recharts tooltip content — every chart in the analytics tabs uses this instead of
 * Recharts' default box, so hover styling stays consistent and themed (see dataviz skill:
 * "values lead, labels follow"; "line keys, not boxes"). */
export default function ChartTooltip({ active, label, items }: ChartTooltipProps) {
  if (!active || items.length === 0) return null;
  return (
    <div className="bg-surface border border-border rounded-lg shadow-elevated dark:shadow-elevated-dark px-3 py-2 text-xs min-w-[120px]">
      {label != null && <div className="text-muted-foreground mb-1">{label}</div>}
      {items.map((item, i) => (
        <div key={i} className="flex items-center gap-2 justify-between">
          <span className="flex items-center gap-1.5 text-muted-foreground">
            <span className="inline-block w-2.5 h-[2px] rounded-full" style={{ backgroundColor: item.color }} />
            {item.name}
          </span>
          <span className="font-semibold text-foreground">{item.value}</span>
        </div>
      ))}
    </div>
  );
}
