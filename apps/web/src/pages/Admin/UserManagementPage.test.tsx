import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import {
  useAdminUsers,
  useChangeUserRole,
  useCreateAdminUser,
  useDeleteAdminUser,
  useSetUserActive,
} from '@/api/hooks/useAdminUsers'
import UserManagementPage from './UserManagementPage'
import type { AdminUserPage, AdminUserSummary } from '@/api/types'

vi.mock('@/api/hooks/useAdminUsers', () => ({
  useAdminUsers: vi.fn(),
  useChangeUserRole: vi.fn(),
  useCreateAdminUser: vi.fn(),
  useDeleteAdminUser: vi.fn(),
  useSetUserActive: vi.fn(),
}))

const mockedUseAdminUsers = vi.mocked(useAdminUsers)
const mockedUseChangeUserRole = vi.mocked(useChangeUserRole)
const mockedUseCreateAdminUser = vi.mocked(useCreateAdminUser)
const mockedUseDeleteAdminUser = vi.mocked(useDeleteAdminUser)
const mockedUseSetUserActive = vi.mocked(useSetUserActive)

const mutation = {
  mutate: vi.fn(),
  isPending: false,
  isError: false,
  error: null,
  reset: vi.fn(),
}

function makeUser(overrides: Partial<AdminUserSummary> = {}): AdminUserSummary {
  return {
    id: 'user-1',
    email: 'john@example.com',
    displayName: 'John',
    role: 'LEARNER',
    active: true,
    emailVerified: false,
    ...overrides,
  }
}

function makePage(items: AdminUserSummary[] = []): AdminUserPage {
  return { items, totalElements: items.length, page: 0, size: 25, totalPages: 1 }
}

function setupUsers(page = makePage()) {
  mockedUseAdminUsers.mockReturnValue({
    data: page,
    isLoading: false,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useAdminUsers>)
}

describe('UserManagementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedUseChangeUserRole.mockReturnValue(mutation as unknown as ReturnType<typeof useChangeUserRole>)
    mockedUseCreateAdminUser.mockReturnValue(mutation as unknown as ReturnType<typeof useCreateAdminUser>)
    mockedUseDeleteAdminUser.mockReturnValue(mutation as unknown as ReturnType<typeof useDeleteAdminUser>)
    mockedUseSetUserActive.mockReturnValue(mutation as unknown as ReturnType<typeof useSetUserActive>)
  })

  it('shows the loading state while the directory is loading', () => {
    setupUsers()
    mockedUseAdminUsers.mockReturnValue({ isLoading: true, isError: false } as ReturnType<typeof useAdminUsers>)

    render(<UserManagementPage />)

    expect(screen.getByText('Loading...')).toBeInTheDocument()
  })

  it('renders a paginated user directory with account verification state', () => {
    setupUsers(makePage([
      makeUser(),
      makeUser({ id: 'user-2', displayName: 'Alex', email: 'alex@example.com', active: false, emailVerified: true }),
    ]))

    render(<UserManagementPage />)

    expect(screen.getByText('People and access')).toBeInTheDocument()
    expect(screen.getByText('John')).toBeInTheDocument()
    expect(screen.getByText('Alex')).toBeInTheDocument()
    expect(screen.getByText('Pending')).toBeInTheDocument()
    expect(screen.getByText('Confirmed')).toBeInTheDocument()
  })

  it('opens the invitation and test-account form', () => {
    setupUsers()

    render(<UserManagementPage />)
    fireEvent.click(screen.getByRole('button', { name: 'Add person' }))

    expect(screen.getByRole('dialog', { name: 'Add a person' })).toBeInTheDocument()
    expect(screen.getByLabelText('Temporary password')).toBeInTheDocument()
    expect(screen.getByLabelText('Create as a test account')).toBeInTheDocument()
  })
})