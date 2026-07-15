import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { BellOff, Plus, Trash2, Tag } from 'lucide-react';
import {
  getSubscriptions,
  createSubscription,
  deleteSubscription,
  type TopicSubscription,
} from '../../services/topicSubscriptionService';
import { useAuth } from '../../context/AuthContext';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import PageHeader from '../ui/PageHeader';
import { Card, CardBody } from '../ui/Card';
import Button from '../ui/Button';
import IconButton from '../ui/IconButton';
import Badge from '../ui/Badge';
import Input from '../ui/Input';
import EmptyState from '../ui/EmptyState';
import { SkeletonRow } from '../ui/Skeleton';
import { useToast } from '../ui/Toast';

export default function SubscriptionsPage() {
  useDocumentTitle('Subscriptions');
  const { token } = useAuth();
  const toast = useToast();
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
      toast.success(`Subscribed to "${result.data.topic}"`);
    } else {
      toast.error(result.error ?? 'Failed to create subscription.');
    }
    setAdding(false);
  };

  const handleDelete = async (id: string) => {
    const result = await deleteSubscription(id, token!);
    if (result.success) {
      setSubscriptions(prev => prev.filter(s => s.id !== id));
      toast.success('Subscription removed');
    } else {
      toast.error(result.error ?? 'Failed to remove subscription');
    }
  };

  return (
    <div className="min-h-full bg-background">
      <div className="max-w-2xl mx-auto py-8 px-4">
        <motion.div variants={staggerContainer} initial="hidden" animate="visible">
          <PageHeader
            title="Topic Subscriptions"
            description="Get notified when documentation matching your topics is updated or added."
          />

          {/* Add subscription form */}
          <motion.div variants={fadeInUp}>
            <Card className="mb-5">
              <CardBody>
                <h2 className="text-sm font-semibold text-foreground mb-3">Subscribe to a topic</h2>
                <form onSubmit={handleAdd} className="space-y-2">
                  <Input
                    aria-label="Topic"
                    placeholder="Topic (e.g. LDAP authentication, installation)"
                    value={form.topic}
                    onChange={e => setForm(f => ({ ...f, topic: e.target.value }))}
                  />
                  <div className="flex gap-2">
                    <Input
                      className="flex-1"
                      aria-label="Product (optional)"
                      placeholder="Product (optional)"
                      value={form.product}
                      onChange={e => setForm(f => ({ ...f, product: e.target.value }))}
                    />
                    <Input
                      className="w-32"
                      aria-label="Version"
                      placeholder="Version"
                      value={form.version}
                      onChange={e => setForm(f => ({ ...f, version: e.target.value }))}
                    />
                  </div>
                  {formError && <p className="text-xs text-danger">{formError}</p>}
                  <Button type="submit" variant="primary" leftIcon={<Plus size={14} />} loading={adding} disabled={adding}>
                    Subscribe
                  </Button>
                </form>
              </CardBody>
            </Card>
          </motion.div>

          {/* Subscriptions list */}
          {loading && (
            <div className="space-y-2">
              <SkeletonRow columns={3} />
              <SkeletonRow columns={3} />
            </div>
          )}

          {!loading && subscriptions.length === 0 && (
            <EmptyState
              icon={BellOff}
              title="No subscriptions yet"
              description="Subscribe to topics to get notified when relevant docs are updated."
            />
          )}

          <motion.div variants={fadeInUp} className="space-y-2">
            {subscriptions.map(sub => (
              <Card key={sub.id} className="flex items-center gap-3 p-3.5">
                <Tag size={15} className="text-primary flex-shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-foreground truncate">{sub.topic}</p>
                  <div className="flex items-center gap-1.5 mt-0.5">
                    {sub.product && (
                      <Badge variant="primary">
                        {sub.product}{sub.version ? ` ${sub.version}` : ''}
                      </Badge>
                    )}
                    {!sub.product && (
                      <span className="text-xs text-muted-foreground">All products</span>
                    )}
                  </div>
                </div>
                <IconButton
                  label="Remove subscription"
                  variant="danger"
                  size="sm"
                  className="flex-shrink-0"
                  onClick={() => handleDelete(sub.id)}
                >
                  <Trash2 size={14} />
                </IconButton>
              </Card>
            ))}
          </motion.div>
        </motion.div>
      </div>
    </div>
  );
}
