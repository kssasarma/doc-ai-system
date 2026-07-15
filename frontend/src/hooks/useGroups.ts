import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../context/AuthContext';
import { listGroups } from '../services/groupService';

/** Shared react-query hook — same dedup rationale as useTenantUsers (Phase 6.1). Optional `q`
 * backs the Combobox group picker's typeahead search (Phase 6.4). */
export function useGroups(q?: string) {
  const { token } = useAuth();
  return useQuery({
    queryKey: ['groups', q ?? ''],
    queryFn: () => listGroups(token!, q),
    enabled: !!token,
  });
}
