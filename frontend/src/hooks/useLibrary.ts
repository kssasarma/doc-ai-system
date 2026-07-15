import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../context/AuthContext';
import { fetchLibrary } from '../services/libraryService';

export function useLibrary(q?: string) {
  const { token } = useAuth();
  return useQuery({
    queryKey: ['library', q ?? ''],
    queryFn: () => fetchLibrary(token!, q),
    enabled: !!token,
  });
}
