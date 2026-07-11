import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { bootstrap } from '../../services/authService';
import { useAuth } from '../../context/AuthContext';
import AuthLayout from './AuthLayout';
import Input from '../ui/Input';
import Button from '../ui/Button';

export default function BootstrapPage() {
  const { applySession } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [alreadyInitialized, setAlreadyInitialized] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setAlreadyInitialized(false);
    setIsSubmitting(true);
    try {
      const data = await bootstrap(username, email, password);
      if (data.error || !data.token) {
        if (data.error?.toLowerCase().includes('already')) {
          setAlreadyInitialized(true);
        } else {
          setError(data.error || 'Setup failed');
        }
        return;
      }
      applySession(data);
      navigate('/admin', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title="First-time setup"
      subtitle="Create the initial super admin account. This only works once, before any user exists."
      footer={<p className="text-xs text-muted-foreground text-center"><Link to="/" className="text-primary hover:underline">Back to sign in</Link></p>}
    >
      {alreadyInitialized ? (
        <div className="text-sm text-foreground bg-warning/10 border border-warning/20 rounded-lg px-3 py-3 space-y-2">
          <p>This system has already been set up.</p>
          <Link to="/" className="text-primary hover:underline font-medium">Go to sign in →</Link>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <Input
            label="Username" type="text" value={username} onChange={e => setUsername(e.target.value)}
            required minLength={3} maxLength={50} autoFocus placeholder="e.g. admin"
          />
          <Input
            label="Email" type="email" value={email} onChange={e => setEmail(e.target.value)}
            required placeholder="you@company.com"
          />
          <Input
            label="Password" type="password" value={password} onChange={e => setPassword(e.target.value)}
            required minLength={6} placeholder="At least 6 characters"
          />

          {error && <div className="text-sm text-danger bg-danger/10 rounded-lg px-3 py-2">{error}</div>}

          <Button type="submit" loading={isSubmitting} className="w-full" size="lg">
            {isSubmitting ? 'Setting up…' : 'Create super admin account'}
          </Button>
        </form>
      )}
    </AuthLayout>
  );
}
