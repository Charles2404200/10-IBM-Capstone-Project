import { Button, Column, Grid, Heading, InlineNotification, InlineLoading, Select, SelectItem, Tag, Modal, Form, Stack, TextInput } from '@carbon/react'
import { Renew, UserAdmin, Add } from '@carbon/icons-react'
import { useState } from 'react'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useChangeUserRole, useAdminUsers, useSetUserActive, useCreateAdminUser } from '@/api/hooks/useAdminUsers'
import type { AdminUserSummary, UserRole } from '@/api/types'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import styles from './AdminOperationsPage.module.css'
import axios from 'axios'

const ROLES: UserRole[] = ['LEARNER', 'SCENARIO_AUTHOR', 'REVIEWER', 'ADMINISTRATOR']
const createUserSchema = z.object({
  displayName: z.string().min(2, 'Name must be at least 2 characters'),
  email: z.string().email('Enter a valid email'),
  role: z.enum([
    'LEARNER',
    'SCENARIO_AUTHOR',
    'REVIEWER',
    'ADMINISTRATOR',
  ]),
})

type CreateUserFormValues = z.infer<typeof createUserSchema>

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
        {ROLES.map((option) => <SelectItem key={option} value={option} text={option.toLowerCase().replace('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())} />)}
      </Select>
    </td>
    <td><div className={styles.actions}>
      <Button kind="tertiary" size="sm" disabled={saving || role === user.role} onClick={() => changeRole.mutate({ userId: user.id, role })}>Save role</Button>
      <Button kind={user.active ? 'danger--tertiary' : 'tertiary'} size="sm" disabled={saving} onClick={() => setActive.mutate({ userId: user.id, active: !user.active })}>{user.active ? 'Deactivate' : 'Reactivate'}</Button>
    </div></td>
  </tr>
}

function CreateUserModal({ open, onClose } : { open: boolean; onClose: () => void }) {
  const createUser = useCreateAdminUser()
  const [showSuccess, setShowSuccess] = useState(false)
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isValid },
  } = useForm<CreateUserFormValues>({
    resolver: zodResolver(createUserSchema),
    mode: 'onChange',
    defaultValues: {
      displayName: '',
      email: '',
      role: 'SCENARIO_AUTHOR',
    },
  })

  // handles duplicate email error and generic failure error from the API
  const errorDetail = createUser.isError && axios.isAxiosError(createUser.error) ? createUser.error.response?.data?.detail : undefined
  const duplicateEmail = createUser.isError && typeof errorDetail === 'string' && errorDetail.toLowerCase().includes('already exists')
  const genericFailure = createUser.isError && !duplicateEmail

  function handleClose() {
    reset()
    createUser.reset()
    setShowSuccess(false)
    onClose()
  }

  function onSubmit(data: CreateUserFormValues) {
    if (createUser.isPending) return

    createUser.mutate(data, {
      onSuccess: () => {
        setShowSuccess(true)
        reset()
      },
    })
  }

  return (
    <Modal
      open={open}
      modalHeading="Create User Account"
      primaryButtonText={createUser.isPending ? 'Creating...' : 'Create Account'}
      secondaryButtonText="Cancel"
      primaryButtonDisabled={!isValid || createUser.isPending}
      onRequestClose={handleClose}
      onSecondarySubmit={handleClose}
      onRequestSubmit={handleSubmit(onSubmit)}
    >
      <Form>
        <Stack gap={5}>
          <TextInput id="displayName" labelText="Name" placeholder="Enter the user's name" invalid={Boolean(errors.displayName)} invalidText={errors.displayName?.message} {...register('displayName')} />
          <TextInput id="email" labelText="Email" placeholder="Enter the user's email" invalid={Boolean(errors.email)} invalidText={errors.email?.message} {...register('email')} />
          <Select id="role" labelText="Role" {...register('role')}>
            {ROLES.map((role) => (<SelectItem key={role} value={role} text={role.toLowerCase().replace('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())} />))}
          </Select>
          {showSuccess && (
            <InlineNotification kind="success" title="Account created" subtitle="The user account was created and an account setup email has been sent." hideCloseButton />
          )}
          {duplicateEmail && (
            <InlineNotification kind="error" title="Email already in use" subtitle="An account with this email already exists." hideCloseButton />
          )}
          {genericFailure && (
            <InlineNotification kind="error" title="Couldn't create the account" subtitle="Something went wrong. Please try again." hideCloseButton />
          )}
        </Stack>
      </Form>
    </Modal>
  )
}

export default function UserManagementPage() {
  const users = useAdminUsers()
  const [showCreateModal, setShowCreateModal] = useState(false)
  if (users.isLoading) return <LoadingState />
  if (users.isError) return <ErrorState />
  const entries = users.data ?? []

  return <main className={styles.page}>
    <Grid condensed><Column lg={16} md={8} sm={4}>
      <header className={styles.header}><div><p className={styles.eyebrow}>Access control</p><Heading>People and access</Heading><p>Assign the least privilege needed to author content, review work or administer the platform.</p></div>
        <div className={styles.actions}>
          <Button kind="primary" renderIcon={Add} onClick={() => setShowCreateModal(true)}>Create User</Button>
          <Button kind="tertiary" renderIcon={Renew} disabled={users.isFetching} onClick={() => users.refetch()}>Refresh</Button>
        </div>
      </header>
      <section className={styles.summary}><UserAdmin size={24}/><span><strong>{entries.filter((user) => user.active).length} active accounts</strong> across {entries.length} registered users</span></section>
      {users.isFetching && <InlineLoading description="Refreshing users" />}
      <div className={styles.tableWrap}><table className={styles.table}><thead><tr><th>User</th><th>Status</th><th>Role</th><th>Actions</th></tr></thead><tbody>{entries.map((user) => <UserRow key={user.id} user={user} />)}</tbody></table></div>
      {entries.length === 0 && <InlineNotification kind="info" title="No users found" subtitle="Registered accounts will appear here." hideCloseButton />}
    </Column></Grid>
    <CreateUserModal open={showCreateModal} onClose={() => setShowCreateModal(false)} />
  </main>
}
