import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import AuthLayout from './AuthLayout';
import PasswordInput from '../ui/PasswordInput';
import Button from '../ui/Button';

export default function ChangePasswordPage() {
  const { user, changePassword, logout } = useAuth();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (newPassword !== confirmPassword) {
      setError('New passwords do not match');
      return;
    }
    if (newPassword === currentPassword) {
      setError('New password must be different from the current password');
      return;
    }
    setIsSubmitting(true);
    try {
      await changePassword(currentPassword, newPassword);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title="Set a new password"
      subtitle={
        user?.mustChangePassword
          ? 'This account was created with a temporary password. Choose a new one to continue.'
          : 'Choose a new password for your account.'
      }
      footer={
        <p className="text-xs text-muted-foreground text-center">
          <button type="button" onClick={logout} className="text-primary hover:underline">
            Sign out
          </button>
        </p>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <PasswordInput
          label="Current password"
          value={currentPassword}
          onChange={e => setCurrentPassword(e.target.value)}
          required
          autoFocus
          placeholder="Enter current password"
        />
        <PasswordInput
          label="New password"
          value={newPassword}
          onChange={e => setNewPassword(e.target.value)}
          required
          minLength={10}
          showStrength
          placeholder="At least 10 characters"
        />
        <PasswordInput
          label="Confirm new password"
          value={confirmPassword}
          onChange={e => setConfirmPassword(e.target.value)}
          required
          minLength={10}
          placeholder="Re-enter new password"
        />

        {error && <div className="text-sm text-danger bg-danger/10 rounded-lg px-3 py-2">{error}</div>}

        <Button type="submit" loading={isSubmitting} className="w-full" size="lg">
          {isSubmitting ? 'Updating…' : 'Update password'}
        </Button>
      </form>
    </AuthLayout>
  );
}
