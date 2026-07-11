import React from 'react';
import { motion } from 'framer-motion';
import { fadeInUp } from '../../lib/motion';
import { cn } from '../../lib/cn';

export default function PageHeader({
  title, description, actions, className,
}: {
  title: string;
  description?: string;
  actions?: React.ReactNode;
  className?: string;
}) {
  return (
    <motion.div
      variants={fadeInUp}
      initial="hidden"
      animate="visible"
      className={cn('flex items-start justify-between gap-4 mb-6', className)}
    >
      <div>
        <h1 className="text-lg font-semibold text-foreground">{title}</h1>
        {description && <p className="text-sm text-muted-foreground mt-1">{description}</p>}
      </div>
      {actions && <div className="flex items-center gap-2 flex-shrink-0">{actions}</div>}
    </motion.div>
  );
}
