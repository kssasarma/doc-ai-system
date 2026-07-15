import { QueryClient } from '@tanstack/react-query';

/** Conservative defaults: don't refetch just because the window regained focus (this app's data
 * doesn't change that often and the resulting request storm on every tab-switch isn't worth it),
 * and don't retry failed requests automatically — most failures here are auth/permission errors
 * that a retry won't fix, and the existing raw-fetch code this replaces never retried either. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: false,
      staleTime: 30_000,
    },
  },
});
