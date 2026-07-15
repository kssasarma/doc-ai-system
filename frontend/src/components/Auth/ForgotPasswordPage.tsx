import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { forgotPassword } from '../../services/authService';
import AuthLayout from './AuthLayout';
import Input from '../ui/Input';
import Button from '../ui/Button';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      await forgotPassword(email);
    } finally {
      // Always show the same confirmation regardless of outcome — the backend gives no signal
      // either way, so this page can't (and shouldn't try to) distinguish "email sent" from
      // "no such account" without becoming a user-enumeration oracle.
      setIsSubmitting(false);
      setSubmitted(true);
    }
  };

  if (submitted) {
    return (
      <AuthLayout title="Check your email" subtitle="">
        <div className="text-center space-y-4">
          <p className="text-sm text-muted-foreground">
            If an account exists for <span className="font-medium text-foreground">{email}</span>, we've sent a
            password reset link. It expires in 1 hour.
          </p>
          <Link to="/" className="text-primary hover:underline text-sm font-medium inline-block">Back to sign in</Link>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Forgot your password?" subtitle="Enter your email and we'll send you a reset link.">
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input
          label="Email"
          type="email"
          value={email}
          onChange={e => setEmail(e.target.value)}
          required
          autoFocus
          placeholder="you@example.com"
        />
        <Button type="submit" loading={isSubmitting} className="w-full" size="lg">
          {isSubmitting ? 'Sending…' : 'Send reset link'}
        </Button>
        <Link to="/" className="text-primary hover:underline text-sm font-medium block text-center">Back to sign in</Link>
      </form>
    </AuthLayout>
  );
}
