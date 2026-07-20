import React from 'react';
import { motion } from 'framer-motion';
import { BookOpen } from 'lucide-react';
import { useBranding } from '../../context/BrandingContext';
import ThemeToggle from '../ui/ThemeToggle';
import { scaleIn } from '../../lib/motion';
import Footer from '../Layout/Footer';

/** Shared shell for the auth screens (Login, Accept-invite, Change-password) — a centered
 * card over a subtle branded background, with the tenant's logo when one is configured. */
export default function AuthLayout({
  title, subtitle, children, footer,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}) {
  const branding = useBranding();

  return (
    <div className="min-h-screen flex flex-col bg-background relative overflow-hidden">
      {/* Subtle branded background glow */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 opacity-[0.15] dark:opacity-[0.25]"
        style={{
          backgroundImage:
            'radial-gradient(circle at 20% 20%, rgb(var(--color-primary)) 0%, transparent 40%), radial-gradient(circle at 80% 80%, rgb(var(--color-accent)) 0%, transparent 40%)',
        }}
      />
      <div className="absolute top-4 right-4">
        <ThemeToggle />
      </div>

      <div className="flex-1 flex items-center justify-center px-4">
        <motion.div
          variants={scaleIn}
          initial="hidden"
          animate="visible"
          className="relative bg-surface border border-border rounded-2xl shadow-elevated dark:shadow-elevated-dark p-8 w-full max-w-sm"
        >
          <div className="text-center mb-8">
            {branding.logoUrl ? (
              <img src={branding.logoUrl} alt={branding.productName} className="h-12 mx-auto mb-3 object-contain" />
            ) : (
              <div className="w-12 h-12 rounded-2xl bg-primary/10 flex items-center justify-center mx-auto mb-3">
                <BookOpen size={22} className="text-primary" />
              </div>
            )}
            <h1 className="text-2xl font-bold text-foreground">{title}</h1>
            <p className="text-sm text-muted-foreground mt-1">{subtitle}</p>
          </div>

          {children}

          {footer && <div className="mt-6">{footer}</div>}
        </motion.div>
      </div>

      <Footer />
    </div>
  );
}
