import { Button, Column, Grid, Heading, InlineNotification, InlineLoading, Select, SelectItem, Tag } from '@carbon/react'
import { Renew, UserAdmin } from '@carbon/icons-react'
import { useState } from 'react'
import { useChangeUserRole, useAdminUsers, useSetUserActive } from '@/api/hooks/useAdminUsers'
import type { AdminUserSummary, UserRole } from '@/api/types'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import styles from './AdminOperationsPage.module.css'

const ROLES: UserRole[] = ['LEARNER', 'SCENARIO_AUTHOR', 'REVIEWER', 'ADMINISTRATOR']

function UserRow({ user }: { user: AdminUserSummary }) {
  const changeRole = useChangeUserRole()
  const setActive = useSetUserActive()
  const [role, setRole] = useState<UserRole>(user.role)
  const saving = changeRole.isPending || setActive.isPending

  return <tr>
    <td><strong>{user.displayName}</strong><span>{user.email}</span></td>
    <td><Tag type={user.active ? 'green' : 'gray'}>{user.active ? 'Active' : 'Inactive'}</Tag></td>
    <td>
      <Select id={`role-${user.id}`} aria-label={`Role for ${user.displayName}`} value={role} size="sm" onChange={(event) => setRole(event.target.value as UserRole)}>
        {ROLES.map((option) => <SelectItem key={option} value={option} text={option.replace('_', ' ')} />)}
      </Select>
    </td>
    <td><div className={styles.actions}>
      <Button kind="tertiary" size="sm" disabled={saving || role === user.role} onClick={() => changeRole.mutate({ userId: user.id, role })}>Save role</Button>
      <Button kind={user.active ? 'danger--tertiary' : 'tertiary'} size="sm" disabled={saving} onClick={() => setActive.mutate({ userId: user.id, active: !user.active })}>{user.active ? 'Deactivate' : 'Reactivate'}</Button>
    </div></td>
  </tr>
}

export default function UserManagementPage() {
  const users = useAdminUsers()
  if (users.isLoading) return <LoadingState />
  if (users.isError) return <ErrorState />
  const entries = users.data ?? []

  return <main className={styles.page}>
    <Grid condensed><Column lg={16} md={8} sm={4}>
      <header className={styles.header}><div><p className={styles.eyebrow}>Access control</p><Heading>People and access</Heading><p>Assign the least privilege needed to author content, review work or administer the platform.</p></div><Button kind="tertiary" renderIcon={Renew} disabled={users.isFetching} onClick={() => users.refetch()}>Refresh</Button></header>
      <section className={styles.summary}><UserAdmin size={24}/><span><strong>{entries.filter((user) => user.active).length} active accounts</strong> across {entries.length} registered users</span></section>
      {users.isFetching && <InlineLoading description="Refreshing users" />}
      <div className={styles.tableWrap}><table className={styles.table}><thead><tr><th>User</th><th>Status</th><th>Role</th><th>Actions</th></tr></thead><tbody>{entries.map((user) => <UserRow key={user.id} user={user} />)}</tbody></table></div>
      {entries.length === 0 && <InlineNotification kind="info" title="No users found" subtitle="Registered accounts will appear here." hideCloseButton />}
    </Column></Grid>
  </main>
}
