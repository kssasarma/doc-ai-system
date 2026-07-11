import React from 'react';
import { motion } from 'framer-motion';
import type { LucideIcon } from 'lucide-react';
import { scaleIn } from '../../lib/motion';
import { cn } from '../../lib/cn';

export default function EmptyState({
  icon: Icon, title, description, action, className,
}: {
  icon: LucideIcon;
  title: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <motion.div
      variants={scaleIn}
      initial="hidden"
      animate="visible"
      className={cn('flex flex-col items-center justify-center text-center py-14 px-6', className)}
    >
      <div className="w-12 h-12 rounded-full bg-muted flex items-center justify-center mb-4">
        <Icon size={22} className="text-muted-foreground" />
      </div>
      <p className="text-sm font-medium text-foreground">{title}</p>
      {description && <p className="text-xs text-muted-foreground mt-1 max-w-xs">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </motion.div>
  );
}
