import React, { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { resetPassword } from '../../services/authService';
import AuthLayout from './AuthLayout';
import PasswordInput from '../ui/PasswordInput';
import Button from '../ui/Button';

export default function ResetPasswordPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  if (!token) {
    return (
      <AuthLayout title="Reset password" subtitle="">
        <div className="text-center">
          <p className="text-sm text-danger">This reset link is missing its token.</p>
          <Link to="/forgot-password" className="text-primary hover:underline text-sm font-medium mt-4 inline-block">
            Request a new link
          </Link>
        </div>
      </AuthLayout>
    );
  }

  if (done) {
    return (
      <AuthLayout title="Password reset" subtitle="">
        <div className="text-center space-y-4">
          <p className="text-sm text-muted-foreground">Your password has been reset. You can now sign in.</p>
          <Button onClick={() => navigate('/', { replace: true })} className="w-full" size="lg">
            Go to sign in
          </Button>
        </div>
      </AuthLayout>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (newPassword !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }
    setIsSubmitting(true);
    try {
      const result = await resetPassword(token, newPassword);
      if (!result.success) {
        setError(result.error || 'Could not reset password');
        return;
      }
      setDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthLayout title="Reset your password" subtitle="Choose a new password for your account.">
      <form onSubmit={handleSubmit} className="space-y-4">
        <PasswordInput
          label="New password" value={newPassword} onChange={e => setNewPassword(e.target.value)}
          required minLength={10} autoFocus showStrength placeholder="At least 10 characters"
        />
        <PasswordInput
          label="Confirm password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)}
          required minLength={10} placeholder="Re-enter password"
        />

        {error && <div className="text-sm text-danger bg-danger/10 rounded-lg px-3 py-2">{error}</div>}

        <Button type="submit" loading={isSubmitting} className="w-full" size="lg">
          {isSubmitting ? 'Resetting…' : 'Reset password'}
        </Button>
      </form>
    </AuthLayout>
  );
}
