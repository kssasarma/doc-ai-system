import type { Transition, Variants } from 'framer-motion';

/** Shared easing/timing so every animated surface in the app feels consistent. */
export const EASE_OUT: Transition['ease'] = [0.16, 1, 0.3, 1];

export const fadeIn: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { duration: 0.2, ease: EASE_OUT } },
};

export const fadeInUp: Variants = {
  hidden: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.28, ease: EASE_OUT } },
};

export const scaleIn: Variants = {
  hidden: { opacity: 0, scale: 0.96 },
  visible: { opacity: 1, scale: 1, transition: { duration: 0.2, ease: EASE_OUT } },
  exit: { opacity: 0, scale: 0.97, transition: { duration: 0.15, ease: EASE_OUT } },
};

export const staggerContainer: Variants = {
  hidden: {},
  visible: {
    transition: { staggerChildren: 0.05, delayChildren: 0.02 },
  },
};

export const modalBackdrop: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { duration: 0.15 } },
  exit: { opacity: 0, transition: { duration: 0.12 } },
};

export const modalPanel: Variants = {
  hidden: { opacity: 0, scale: 0.96, y: 8 },
  visible: { opacity: 1, scale: 1, y: 0, transition: { duration: 0.22, ease: EASE_OUT } },
  exit: { opacity: 0, scale: 0.97, y: 4, transition: { duration: 0.15, ease: EASE_OUT } },
};

export const slideDown: Variants = {
  hidden: { opacity: 0, y: -6, scale: 0.98 },
  visible: { opacity: 1, y: 0, scale: 1, transition: { duration: 0.16, ease: EASE_OUT } },
  exit: { opacity: 0, y: -4, scale: 0.98, transition: { duration: 0.12, ease: EASE_OUT } },
};
