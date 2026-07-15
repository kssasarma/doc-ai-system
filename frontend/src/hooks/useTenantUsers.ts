import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../context/AuthContext';
import { getTenantUsers } from '../services/tenantService';

/** Shared react-query hook so DocumentsTab/UsersPage/GroupsPage/Combobox pickers (previously each
 * fetched this independently — see Phase 6.1) share cached results instead of firing identical
 * calls. `q`/`page`/`size` let callers use it either for typeahead search (small size, changing
 * q) or full pagination (UsersPage) — see Phase 6.4. */
export function useTenantUsers(opts?: { q?: string; page?: number; size?: number }) {
  const { token, user } = useAuth();
  const tenantId = user?.tenantId;
  const page = opts?.page ?? 0;
  const size = opts?.size ?? 20;
  const q = opts?.q ?? '';
  return useQuery({
    queryKey: ['tenant-users', tenantId, q, page, size],
    queryFn: () => getTenantUsers(token!, tenantId!, { q, page, size }),
    enabled: !!token && !!tenantId,
  });
}
