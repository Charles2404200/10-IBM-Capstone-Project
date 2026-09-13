import { Button, Column, Grid, Heading, InlineNotification, InlineLoading, Pagination, Select, SelectItem, Tag, TextInput } from '@carbon/react'
import { Renew, UserAdmin } from '@carbon/icons-react'
import { useDeferredValue, useMemo, useState } from 'react'
import { useChangeUserRole, useAdminUsers, useSetUserActive } from '@/api/hooks/useAdminUsers'
import type { AdminUserSummary, UserRole } from '@/api/types'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import styles from './AdminOperationsPage.module.css'

const ROLES: UserRole[] = ['LEARNER', 'SCENARIO_AUTHOR', 'REVIEWER', 'ADMINISTRATOR']
const PAGE_SIZES = [25, 50, 100]
type AccountStatus = '' | 'active' | 'inactive'

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
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<UserRole | ''>('')
  const [statusFilter, setStatusFilter] = useState<AccountStatus>('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(PAGE_SIZES[0])
  const deferredSearch = useDeferredValue(search)
  const filters = useMemo(() => ({
    search: deferredSearch.trim() || undefined,
    role: roleFilter || undefined,
    active: statusFilter === 'active' ? true : statusFilter === 'inactive' ? false : undefined,
    page: page - 1,
    size: pageSize,
  }), [deferredSearch, page, pageSize, roleFilter, statusFilter])
  const users = useAdminUsers(filters)
  if (users.isLoading) return <LoadingState />
  if (users.isError) return <ErrorState />
  const directory = users.data
  const entries = directory?.items ?? []
  const hasFilters = Boolean(filters.search || filters.role || filters.active !== undefined)

  const updateSearch = (value: string) => {
    setSearch(value)
    setPage(1)
  }

  const updateRole = (value: UserRole | '') => {
    setRoleFilter(value)
    setPage(1)
  }

  const updateStatus = (value: AccountStatus) => {
    setStatusFilter(value)
    setPage(1)
  }

  return <main className={styles.page}>
    <Grid condensed><Column lg={16} md={8} sm={4}>
      <header className={styles.header}><div><p className={styles.eyebrow}>Access control</p><Heading>People and access</Heading><p>Assign the least privilege needed to author content, review work or administer the platform.</p></div><Button kind="tertiary" renderIcon={Renew} disabled={users.isFetching} onClick={() => users.refetch()}>Refresh</Button></header>
      <section className={styles.summary}><UserAdmin size={24}/><span><strong>{directory?.totalElements ?? 0} {hasFilters ? 'matching accounts' : 'registered accounts'}</strong> in this directory</span></section>
      <section className={styles.directoryControls} aria-label="User directory filters">
        <TextInput id="user-search" labelText="Search people" placeholder="Name or email" value={search} onChange={(event) => updateSearch(event.target.value)} />
        <Select id="user-role-filter" labelText="Role" value={roleFilter} onChange={(event) => updateRole(event.target.value as UserRole | '')}>
          <SelectItem value="" text="All roles" />
          {ROLES.map((role) => <SelectItem key={role} value={role} text={role.replace('_', ' ')} />)}
        </Select>
        <Select id="user-status-filter" labelText="Account status" value={statusFilter} onChange={(event) => updateStatus(event.target.value as AccountStatus)}>
          <SelectItem value="" text="All statuses" />
          <SelectItem value="active" text="Active" />
          <SelectItem value="inactive" text="Inactive" />
        </Select>
      </section>
      {users.isFetching && <InlineLoading description="Refreshing users" />}
      <div className={styles.tableWrap}><table className={styles.table}><thead><tr><th>User</th><th>Status</th><th>Role</th><th>Actions</th></tr></thead><tbody>{entries.map((user) => <UserRow key={user.id} user={user} />)}</tbody></table></div>
      {entries.length === 0 && <InlineNotification kind="info" title="No users found" subtitle="Registered accounts will appear here." hideCloseButton />}
      {directory && directory.totalElements > 0 && <Pagination
        className={styles.directoryPagination}
        page={page}
        pageSize={pageSize}
        pageSizes={PAGE_SIZES}
        totalItems={directory.totalElements}
        onChange={({ page: nextPage, pageSize: nextPageSize }) => {
          setPage(nextPage)
          setPageSize(nextPageSize)
        }}
      />}
    </Column></Grid>
  </main>
}
