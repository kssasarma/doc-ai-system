import {
  Combobox as HCombobox, ComboboxInput, ComboboxOptions, ComboboxOption,
} from '@headlessui/react';
import { Search } from 'lucide-react';
import { cn } from '../../lib/cn';

interface ComboboxProps<T> {
  /** Current search text — typically the `q` passed straight to a paginated/search backend
   * endpoint (see useTenantUsers/useGroups), not client-side filtered. */
  query: string;
  onQueryChange: (q: string) => void;
  items: T[];
  getKey: (item: T) => string;
  getLabel: (item: T) => string;
  /** Clears the query automatically after firing — this is a "search then act" picker (add a
   * grant/member), not a persistent single-value select, so there's nothing to keep displayed. */
  onSelect: (item: T) => void;
  placeholder?: string;
  loading?: boolean;
  emptyLabel?: string;
  disabled?: boolean;
}

/** Async-search typeahead picker — replaces the native `<select>` over every tenant user/group
 * (unusable at hundreds of rows) in DocumentAccessManager and GroupsPage (Phase 6.4). True
 * virtualization of the options list is deliberately not layered in here (matching the scope
 * trimmed elsewhere in this phase) — typeahead search already keeps the rendered list small in
 * the common case, since callers pass an already-paginated/searched `items` array. */
export default function Combobox<T>({
  query, onQueryChange, items, getKey, getLabel, onSelect, placeholder, loading, emptyLabel = 'No matches', disabled,
}: ComboboxProps<T>) {
  return (
    <HCombobox
      value={null}
      onChange={(item: T | null) => {
        if (item) {
          onSelect(item);
          onQueryChange('');
        }
      }}
      disabled={disabled}
    >
      <div className="relative">
        <div className="relative">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
          <ComboboxInput
            className="w-full pl-9 pr-3 py-2 text-sm border border-border bg-surface rounded-lg focus:outline-none focus:ring-2 focus:ring-primary text-foreground placeholder:text-muted-foreground disabled:opacity-50"
            placeholder={placeholder}
            displayValue={() => query}
            onChange={e => onQueryChange(e.target.value)}
          />
        </div>
        <ComboboxOptions
          modal={false}
          className="absolute z-20 mt-1 w-full max-h-60 overflow-auto rounded-lg border border-border bg-surface shadow-elevated dark:shadow-elevated-dark py-1 text-sm empty:invisible"
        >
          {loading ? (
            <div className="px-3 py-2 text-xs text-muted-foreground">Searching…</div>
          ) : items.length === 0 ? (
            query.trim() && <div className="px-3 py-2 text-xs text-muted-foreground">{emptyLabel}</div>
          ) : (
            items.map(item => (
              <ComboboxOption
                key={getKey(item)}
                value={item}
                className={({ focus }: { focus: boolean }) => cn(
                  'px-3 py-2 cursor-pointer transition-colors',
                  focus ? 'bg-primary/10 text-primary' : 'text-foreground',
                )}
              >
                {getLabel(item)}
              </ComboboxOption>
            ))
          )}
        </ComboboxOptions>
      </div>
    </HCombobox>
  );
}
