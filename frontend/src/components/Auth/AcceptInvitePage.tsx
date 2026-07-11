import React, { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { acceptInvite } from '../../services/invitationService';
import { useAuth } from '../../context/AuthContext';
import AuthLayout from './AuthLayout';
import Input from '../ui/Input';
import Button from '../ui/Button';

export default function AcceptInvitePage() {
  const { applySession } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!token) {
    return (
      <AuthLayout title="Accept invitation" subtitle="">
        <div className="text-center">
          <p className="text-sm text-danger">This invitation link is missing its token.</p>
          <Link to="/" className="text-primary hover:underline text-sm font-medium mt-4 inline-block">Back to sign in</Link>
        </div>
      </AuthLayout>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }
    setIsSubmitting(true);
    try {
      const data = await acceptInvite(token, username, password);
      if (data.error || !data.token) {
        setError(data.error || 'Could not accept invitation');
        return;
      }
      applySession(data);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthLayout title="Accept invitation" subtitle="Choose a username and password to activate your account.">
      <form onSubmit={handleSubmit} className="space-y-4">
        <p className="text-xs text-muted-foreground bg-muted rounded-lg px-3 py-2">
          Already have an account with this email in another workspace? Enter your existing username and password below to join this workspace too — no new account needed.
        </p>
        <Input
          label="Username" type="text" value={username} onChange={e => setUsername(e.target.value)}
          required minLength={3} maxLength={50} autoFocus placeholder="Choose a username, or your existing one"
        />
        <Input
          label="Password" type="password" value={password} onChange={e => setPassword(e.target.value)}
          required minLength={6} placeholder="At least 6 characters, or your existing password"
        />
        <Input
          label="Confirm password" type="password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)}
          required minLength={6} placeholder="Re-enter password"
        />

        {error && <div className="text-sm text-danger bg-danger/10 rounded-lg px-3 py-2">{error}</div>}

        <Button type="submit" loading={isSubmitting} className="w-full" size="lg">
          {isSubmitting ? 'Activating…' : 'Activate account'}
        </Button>
      </form>
    </AuthLayout>
  );
}
