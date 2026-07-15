import React from 'react';
import { AlertTriangle } from 'lucide-react';
import EmptyState from './ui/EmptyState';
import Button from './ui/Button';

interface ErrorBoundaryProps {
  children: React.ReactNode;
  /** Rendered instead of the default full-page fallback — used for nested boundaries (e.g. around
   * the message list) so one bad message can't take the whole chat shell (sidebar, composer) down
   * with it. */
  fallback?: React.ReactNode;
  /** Shown in the default fallback's description; keep short. */
  label?: string;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

/**
 * No error boundary existed anywhere in this app before — any render throw (a malformed
 * citation payload, a bad markdown table, anything) white-screened the entire SPA with no
 * recovery but a manual refresh. This is the single top-level catch-all; ChatArea additionally
 * wraps just the message list so a single corrupt message degrades gracefully instead of taking
 * the sidebar and composer down with it.
 */
export default class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: unknown, info: React.ErrorInfo) {
    // eslint-disable-next-line no-console
    console.error('ErrorBoundary caught a render error:', error, info.componentStack);
  }

  reset = () => {
    this.setState({ hasError: false });
  };

  render() {
    if (!this.state.hasError) return this.props.children;
    if (this.props.fallback !== undefined) return this.props.fallback;

    return (
      <div className="flex h-full w-full items-center justify-center bg-background">
        <EmptyState
          icon={AlertTriangle}
          title="Something went wrong"
          description={this.props.label ?? "This page hit an unexpected error. You can try again, or reload if that doesn't help."}
          action={
            <div className="flex items-center gap-2">
              <Button variant="outline" onClick={this.reset}>Try again</Button>
              <Button variant="primary" onClick={() => window.location.reload()}>Reload page</Button>
            </div>
          }
        />
      </div>
    );
  }
}
