import { useAuth } from '../../context/AuthContext';
import SuperAdminConsole from './SuperAdminConsole';
import TenantAdminConsole from './TenantAdminConsole';

export default function AdminEntry() {
  const { isSuperAdmin } = useAuth();
  return isSuperAdmin ? <SuperAdminConsole /> : <TenantAdminConsole />;
}
