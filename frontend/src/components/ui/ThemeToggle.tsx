import React from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Sun, Moon } from 'lucide-react';
import { useTheme } from '../../context/ThemeContext';
import IconButton from './IconButton';

export default function ThemeToggle({ variant = 'ghost' }: { variant?: 'ghost' | 'solid' }) {
  const { resolvedTheme, toggle } = useTheme();
  const isDark = resolvedTheme === 'dark';

  return (
    <IconButton
      label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
      variant={variant}
      onClick={toggle}
      className="overflow-hidden"
    >
      <AnimatePresence mode="wait" initial={false}>
        <motion.span
          key={isDark ? 'moon' : 'sun'}
          initial={{ rotate: -90, opacity: 0, scale: 0.5 }}
          animate={{ rotate: 0, opacity: 1, scale: 1 }}
          exit={{ rotate: 90, opacity: 0, scale: 0.5 }}
          transition={{ duration: 0.2 }}
          className="inline-flex"
        >
          {isDark ? <Moon size={16} /> : <Sun size={16} />}
        </motion.span>
      </AnimatePresence>
    </IconButton>
  );
}
