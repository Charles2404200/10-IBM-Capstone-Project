import {
  Button,
  Checkbox,
  Column,
  Grid,
  Heading,
  InlineLoading,
  InlineNotification,
  Modal,
  OverflowMenu,
  OverflowMenuItem,
  Pagination,
  PasswordInput,
  Select,
  SelectItem,
  Tag,
  TextInput,
} from '@carbon/react'
import { Add, CheckmarkFilled, Email, Renew, UserAdmin, UserAvatar } from '@carbon/icons-react'
import { FormEvent, useDeferredValue, useMemo, useState } from 'react'
import {
  useAdminUsers,
  useChangeUserRole,
  useCreateAdminUser,
  useDeleteAdminUser,
  useSetUserActive,
} from '@/api/hooks/useAdminUsers'
import type { AdminUserSummary, UserRole } from '@/api/types'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import styles from './AdminOperationsPage.module.css'

const ROLES: UserRole[] = ['LEARNER', 'SCENARIO_AUTHOR', 'REVIEWER', 'ADMINISTRATOR']
const PAGE_SIZES = [25, 50, 100]
type AccountStatus = '' | 'active' | 'inactive'
type Notice = { kind: 'success' | 'error'; title: string; subtitle: string } | null

const roleLabel = (role: UserRole) => role.split('_').map((part) => part[0] + part.slice(1).toLowerCase()).join(' ')
const errorMessage = (error: unknown, fallback: string) => {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = (error as { response?: { data?: { detail?: string } } }).response
    return response?.data?.detail ?? fallback
  }
  return fallback
}

function UserRow({ user, onNotice, onRequestDelete }: {
  user: AdminUserSummary
  onNotice: (notice: Notice) => void
  onRequestDelete: (user: AdminUserSummary) => void
}) {
  const changeRole = useChangeUserRole()
  const setActive = useSetUserActive()
  const [role, setRole] = useState<UserRole>(user.role)
  const saving = changeRole.isPending || setActive.isPending

  const saveRole = () => changeRole.mutate(
    { userId: user.id, role },
    {
      onSuccess: () => onNotice({ kind: 'success', title: 'Role updated', subtitle: `${user.displayName} is now a ${roleLabel(role)}.` }),
      onError: (error) => onNotice({ kind: 'error', title: 'Role was not updated', subtitle: errorMessage(error, 'Try again in a moment.') }),
    },
  )

  const updateStatus = () => setActive.mutate(
    { userId: user.id, active: !user.active },
    {
      onSuccess: () => onNotice({ kind: 'success', title: user.active ? 'Account deactivated' : 'Account reactivated', subtitle: user.displayName }),
      onError: (error) => onNotice({ kind: 'error', title: 'Account was not updated', subtitle: errorMessage(error, 'Try again in a moment.') }),
    },
  )

  return <tr>
    <td>
      <div className={styles.personCell}>
        <span className={styles.avatar} aria-hidden="true"><UserAvatar size={20} /></span>
        <div><strong>{user.displayName}</strong><span>{user.email}</span></div>
      </div>
    </td>
    <td><Tag type={user.active ? 'green' : 'gray'}>{user.active ? 'Active' : 'Inactive'}</Tag></td>
    <td>
      <span className={styles.verification}>
        {user.emailVerified ? <CheckmarkFilled size={16} /> : <Email size={16} />}
        {user.emailVerified ? 'Confirmed' : 'Pending'}
      </span>
    </td>
    <td>
      <Select id={`role-${user.id}`} aria-label={`Role for ${user.displayName}`} value={role} size="sm"
        onChange={(event) => setRole(event.target.value as UserRole)}>
        {ROLES.map((option) => <SelectItem key={option} value={option} text={roleLabel(option)} />)}
      </Select>
    </td>
    <td>
      <div className={styles.rowActions}>
        <Button kind="tertiary" size="sm" disabled={saving || role === user.role} onClick={saveRole}>Save</Button>
        <OverflowMenu aria-label={`Actions for ${user.displayName}`} size="sm" flipped>
          <OverflowMenuItem itemText={user.active ? 'Deactivate account' : 'Reactivate account'} disabled={saving} onClick={updateStatus} />
          <OverflowMenuItem itemText="Delete user" isDelete disabled={saving} onClick={() => onRequestDelete(user)} />
        </OverflowMenu>
      </div>
    </td>
  </tr>
}

function CreateUserModal({ open, onClose, onCreated, onNotice }: {
  open: boolean
  onClose: () => void
  onCreated: () => void
  onNotice: (notice: Notice) => void
}) {
  const createUser = useCreateAdminUser()
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<UserRole>('LEARNER')
  const [testAccount, setTestAccount] = useState(false)

  const close = () => {
    if (!createUser.isPending) onClose()
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    createUser.mutate({ email, password, displayName, role, skipEmailVerification: testAccount }, {
      onSuccess: (user) => {
        onNotice({
          kind: 'success',
          title: testAccount ? 'Test account created' : 'Invitation created',
          subtitle: testAccount
            ? `${user.email} can sign in immediately with the password you set.`
            : `A confirmation email has been queued for ${user.email}.`,
        })
        onCreated()
        onClose()
      },
      onError: (error) => onNotice({ kind: 'error', title: 'User was not created', subtitle: errorMessage(error, 'Check the details and try again.') }),
    })
  }

  return <Modal open={open} modalHeading="Add a person" primaryButtonText={testAccount ? 'Create test account' : 'Send invitation'}
    secondaryButtonText="Cancel" primaryButtonDisabled={createUser.isPending || !displayName || !email || password.length < 8}
    onRequestClose={close} onRequestSubmit={() => (document.getElementById('create-user-form') as HTMLFormElement | null)?.requestSubmit()}>
    <form id="create-user-form" className={styles.modalForm} onSubmit={submit}>
      <p className={styles.modalIntro}>Set access before the person begins. Standard accounts must confirm their inbox before signing in.</p>
      <TextInput id="new-user-name" labelText="Full name" value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
      <TextInput id="new-user-email" labelText="Email address" type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
      <PasswordInput id="new-user-password" labelText="Temporary password" helperText="At least 8 characters" value={password} onChange={(event) => setPassword(event.target.value)} />
      <Select id="new-user-role" labelText="Platform role" value={role} onChange={(event) => setRole(event.target.value as UserRole)}>
        {ROLES.map((option) => <SelectItem key={option} value={option} text={roleLabel(option)} />)}
      </Select>
      <div className={styles.testAccountToggle}>
        <Checkbox id="new-user-test-account" labelText="Create as a test account" checked={testAccount} onChange={(_, data) => setTestAccount(data.checked)} />
        <p>Skips email confirmation and sends no email. Use this only for controlled testing.</p>
      </div>
    </form>
  </Modal>
}

export default function UserManagementPage() {
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<UserRole | ''>('')
  const [statusFilter, setStatusFilter] = useState<AccountStatus>('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(PAGE_SIZES[0])
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<AdminUserSummary | null>(null)
  const [notice, setNotice] = useState<Notice>(null)
  const deferredSearch = useDeferredValue(search)
  const deleteUser = useDeleteAdminUser()
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

  const resetFilters = () => {
    setSearch('')
    setRoleFilter('')
    setStatusFilter('')
    setPage(1)
  }

  const confirmDelete = () => {
    if (!deleteTarget) return
    deleteUser.mutate(deleteTarget.id, {
      onSuccess: () => {
        setNotice({ kind: 'success', title: 'User deleted', subtitle: `${deleteTarget.displayName} and their learning data have been removed.` })
        setDeleteTarget(null)
      },
      onError: (error) => setNotice({ kind: 'error', title: 'User was not deleted', subtitle: errorMessage(error, 'Try again in a moment.') }),
    })
  }

  return <main className={styles.page}>
    <Grid condensed><Column lg={16} md={8} sm={4}>
      <header className={styles.userHeader}>
        <div><p className={styles.eyebrow}>Access control</p><Heading>People and access</Heading><p>Invite people, prepare test accounts and keep permissions current.</p></div>
        <div className={styles.headerActions}>
          <Button kind="tertiary" renderIcon={Renew} disabled={users.isFetching} onClick={() => users.refetch()}>Refresh</Button>
          <Button renderIcon={Add} onClick={() => setCreateOpen(true)}>Add person</Button>
        </div>
      </header>

      {notice && <InlineNotification kind={notice.kind} title={notice.title} subtitle={notice.subtitle} onCloseButtonClick={() => setNotice(null)} />}

      <section className={styles.userWorkspace}>
        <div className={styles.directoryHeading}>
          <div className={styles.directoryCount}><span className={styles.countIcon}><UserAdmin size={20} /></span><div><strong>{directory?.totalElements ?? 0}</strong><span>{hasFilters ? 'matching accounts' : 'people in the directory'}</span></div></div>
          {hasFilters && <Button kind="ghost" size="sm" onClick={resetFilters}>Clear filters</Button>}
        </div>
        <section className={styles.directoryControls} aria-label="User directory filters">
          <TextInput id="user-search" labelText="Search people" placeholder="Name or email" value={search} onChange={(event) => { setSearch(event.target.value); setPage(1) }} />
          <Select id="user-role-filter" labelText="Role" value={roleFilter} onChange={(event) => { setRoleFilter(event.target.value as UserRole | ''); setPage(1) }}>
            <SelectItem value="" text="All roles" />
            {ROLES.map((role) => <SelectItem key={role} value={role} text={roleLabel(role)} />)}
          </Select>
          <Select id="user-status-filter" labelText="Account status" value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value as AccountStatus); setPage(1) }}>
            <SelectItem value="" text="All statuses" /><SelectItem value="active" text="Active" /><SelectItem value="inactive" text="Inactive" />
          </Select>
        </section>
        {users.isFetching && <InlineLoading className={styles.directoryLoading} description="Updating directory" />}
        <div className={styles.tableWrap}><table className={styles.table}>
          <thead><tr><th>Person</th><th>Account</th><th>Email</th><th>Role</th><th aria-label="Actions" /></tr></thead>
          <tbody>{entries.map((user) => <UserRow key={user.id} user={user} onNotice={setNotice} onRequestDelete={setDeleteTarget} />)}</tbody>
        </table></div>
        {entries.length === 0 && <InlineNotification kind="info" title="No people found" subtitle="Try different filters or add a person." hideCloseButton />}
        {directory && directory.totalElements > 0 && <Pagination className={styles.directoryPagination} page={page} pageSize={pageSize} pageSizes={PAGE_SIZES} totalItems={directory.totalElements}
          onChange={({ page: nextPage, pageSize: nextPageSize }) => { setPage(nextPage); setPageSize(nextPageSize) }} />}
      </section>
    </Column></Grid>

    <CreateUserModal open={createOpen} onClose={() => setCreateOpen(false)} onCreated={() => setPage(1)} onNotice={setNotice} />
    <Modal danger open={Boolean(deleteTarget)} modalHeading="Delete user permanently" primaryButtonText="Delete user" secondaryButtonText="Cancel"
      primaryButtonDisabled={deleteUser.isPending} onRequestClose={() => !deleteUser.isPending && setDeleteTarget(null)} onRequestSubmit={confirmDelete}>
      <p className={styles.deleteCopy}>Delete <strong>{deleteTarget?.displayName}</strong>? This permanently removes the account and any learning work associated with it.</p>
    </Modal>
  </main>
}
