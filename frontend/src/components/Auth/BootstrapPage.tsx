import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';
import { bootstrap } from '../../services/authService';
import { useAuth } from '../../context/AuthContext';

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
    <div className="min-h-screen flex items-center justify-center bg-gray-100 px-4">
      <div className="bg-white rounded-2xl shadow-lg p-8 w-full max-w-sm">
        <div className="text-center mb-8">
          <div className="w-12 h-12 rounded-full bg-blue-50 text-blue-600 flex items-center justify-center mx-auto mb-3">
            <ShieldCheck size={24} />
          </div>
          <h1 className="text-2xl font-bold text-gray-900">First-time setup</h1>
          <p className="text-sm text-gray-500 mt-1">
            Create the initial super admin account. This only works once, before any user exists.
          </p>
        </div>

        {alreadyInitialized ? (
          <div className="text-sm text-gray-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-3 space-y-2">
            <p>This system has already been set up.</p>
            <Link to="/" className="text-blue-600 hover:underline font-medium">Go to sign in →</Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Username</label>
              <input
                type="text" value={username} onChange={e => setUsername(e.target.value)}
                required minLength={3} maxLength={50} autoFocus
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="e.g. admin"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
              <input
                type="email" value={email} onChange={e => setEmail(e.target.value)}
                required
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="you@company.com"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
              <input
                type="password" value={password} onChange={e => setPassword(e.target.value)}
                required minLength={6}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="At least 6 characters"
              />
            </div>

            {error && <div className="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">{error}</div>}

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
            >
              {isSubmitting ? 'Setting up…' : 'Create super admin account'}
            </button>
          </form>
        )}

        <p className="text-xs text-gray-400 text-center mt-6">
          <Link to="/" className="text-blue-500 hover:text-blue-700 hover:underline">Back to sign in</Link>
        </p>
      </div>
    </div>
  );
}
