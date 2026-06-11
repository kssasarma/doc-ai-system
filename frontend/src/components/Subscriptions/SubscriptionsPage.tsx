import React, { useEffect, useState } from 'react';
import { Bell, BellOff, Plus, Trash2, Tag } from 'lucide-react';
import {
  getSubscriptions,
  createSubscription,
  deleteSubscription,
  type TopicSubscription,
} from '../../services/topicSubscriptionService';
import { useAuth } from '../../context/AuthContext';

export default function SubscriptionsPage() {
  const { token } = useAuth();
  const [subscriptions, setSubscriptions] = useState<TopicSubscription[]>([]);
  const [loading, setLoading] = useState(true);
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState({ topic: '', product: '', version: '' });
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    load();
  }, [token]);

  const load = async () => {
    setLoading(true);
    const result = await getSubscriptions(token!);
    if (result.success && result.data) setSubscriptions(result.data);
    setLoading(false);
  };

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.topic.trim()) { setFormError('Topic is required'); return; }
    setFormError(null);
    setAdding(true);
    const result = await createSubscription(
      token!,
      form.topic.trim(),
      form.product.trim() || undefined,
      form.version.trim() || undefined,
    );
    if (result.success && result.data) {
      setSubscriptions(prev => [result.data!, ...prev]);
      setForm({ topic: '', product: '', version: '' });
    } else {
      setFormError(result.error ?? 'Failed to create subscription');
    }
    setAdding(false);
  };

  const handleDelete = async (id: string) => {
    const result = await deleteSubscription(id, token!);
    if (result.success) {
      setSubscriptions(prev => prev.filter(s => s.id !== id));
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-2xl mx-auto py-8 px-4">
        <div className="mb-6">
          <div className="flex items-center gap-2 mb-1">
            <Bell size={22} className="text-blue-600" />
            <h1 className="text-2xl font-bold text-gray-900">Topic Subscriptions</h1>
          </div>
          <p className="text-gray-500 text-sm">
            Get notified when documentation matching your topics is updated or added.
          </p>
        </div>

        {/* Add subscription form */}
        <div className="bg-white border border-gray-200 rounded-xl p-4 mb-5">
          <h2 className="text-sm font-semibold text-gray-700 mb-3">Subscribe to a topic</h2>
          <form onSubmit={handleAdd} className="space-y-2">
            <input
              className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-300"
              placeholder="Topic (e.g. LDAP authentication, installation)"
              value={form.topic}
              onChange={e => setForm(f => ({ ...f, topic: e.target.value }))}
            />
            <div className="flex gap-2">
              <input
                className="flex-1 px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-300"
                placeholder="Product (optional)"
                value={form.product}
                onChange={e => setForm(f => ({ ...f, product: e.target.value }))}
              />
              <input
                className="w-32 px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-300"
                placeholder="Version"
                value={form.version}
                onChange={e => setForm(f => ({ ...f, version: e.target.value }))}
              />
            </div>
            {formError && <p className="text-xs text-red-500">{formError}</p>}
            <button
              type="submit"
              disabled={adding}
              className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              <Plus size={14} /> Subscribe
            </button>
          </form>
        </div>

        {/* Subscriptions list */}
        {loading && (
          <div className="flex justify-center py-8">
            <div className="animate-spin rounded-full h-7 w-7 border-b-2 border-blue-600" />
          </div>
        )}

        {!loading && subscriptions.length === 0 && (
          <div className="text-center py-12 text-gray-400">
            <BellOff size={36} className="mx-auto mb-2 opacity-30" />
            <p className="font-medium">No subscriptions yet</p>
            <p className="text-sm mt-1">Subscribe to topics to get notified when relevant docs are updated.</p>
          </div>
        )}

        <div className="space-y-2">
          {subscriptions.map(sub => (
            <div key={sub.id} className="flex items-center gap-3 bg-white border border-gray-200 rounded-xl p-3.5">
              <Tag size={15} className="text-blue-500 flex-shrink-0" />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-800 truncate">{sub.topic}</p>
                <div className="flex items-center gap-1.5 mt-0.5">
                  {sub.product && (
                    <span className="text-xs px-2 py-0.5 bg-blue-50 text-blue-600 rounded-full">
                      {sub.product}{sub.version ? ` ${sub.version}` : ''}
                    </span>
                  )}
                  {!sub.product && (
                    <span className="text-xs text-gray-400">All products</span>
                  )}
                </div>
              </div>
              <button
                onClick={() => handleDelete(sub.id)}
                className="p-1.5 rounded-lg text-gray-400 hover:text-red-500 hover:bg-red-50 transition-colors flex-shrink-0"
                title="Remove subscription"
              >
                <Trash2 size={14} />
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
