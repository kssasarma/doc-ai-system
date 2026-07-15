import { cn } from '../../lib/cn';

export function Skeleton({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        'animate-shimmer rounded-md bg-gradient-to-r from-muted via-surface-hover to-muted bg-[length:200%_100%]',
        className,
      )}
    />
  );
}

export function SkeletonText({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <div className={cn('space-y-2', className)}>
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} className={cn('h-3.5', i === lines - 1 ? 'w-2/3' : 'w-full')} />
      ))}
    </div>
  );
}

export function SkeletonCard({ className }: { className?: string }) {
  return (
    <div className={cn('rounded-xl border border-border bg-surface p-5', className)}>
      <Skeleton className="h-4 w-1/3 mb-3" />
      <SkeletonText lines={2} />
    </div>
  );
}

export function SkeletonRow({ columns = 4 }: { columns?: number }) {
  return (
    <div className="flex items-center gap-4 px-6 py-4">
      {Array.from({ length: columns }).map((_, i) => (
        <Skeleton key={i} className={cn('h-3.5', i === 0 ? 'w-1/4' : 'flex-1')} />
      ))}
    </div>
  );
}
