import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useAdminUsers, useChangeUserRole, useCreateAdminUser, useSetUserActive, } from '@/api/hooks/useAdminUsers'
import UserManagementPage from './UserManagementPage'
import type { AdminUserSummary } from '@/api/types'

// mock the admin API hooks
vi.mock('@/api/hooks/useAdminUsers', () => ({
  useAdminUsers: vi.fn(),
  useChangeUserRole: vi.fn(),
  useCreateAdminUser: vi.fn(),
  useSetUserActive: vi.fn(),
}))

// typed mock references
const mockedUseAdminUsers = vi.mocked(useAdminUsers)
const mockedUseChangeUserRole = vi.mocked(useChangeUserRole)
const mockedUseCreateAdminUser = vi.mocked(useCreateAdminUser)
const mockedUseSetUserActive = vi.mocked(useSetUserActive)

// default mutation mocks used by the page
const defaultMutation = {
  mutate: vi.fn(),
  isPending: false,
  isError: false,
  error: null,
  reset: vi.fn(),
}

// sets up the admin users query with default values
// function setupUsers() {
//   mockedUseAdminUsers.mockReturnValue({
//     data: [],
//     isLoading: false,
//     isError: false,
//     isFetching: false,
//     refetch: vi.fn(),
//   } as unknown as ReturnType<typeof useAdminUsers>)
// }

// sets up the admin users query with default values
function setupUsers(overrides: Partial<ReturnType<typeof useAdminUsers>> = {}) {
  mockedUseAdminUsers.mockReturnValue({
    data: [],
    isLoading: false,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useAdminUsers>)
}

// creates a mock admin user for populated-state tests
function makeUser(overrides: Partial<AdminUserSummary> = {}): AdminUserSummary {
  return {
    id: 'user-1',
    email: 'john@example.com',
    displayName: 'John',
    role: 'LEARNER',
    active: true,
    ...overrides,
  }
}

describe('UserManagementPage - create user form validation', () => {
  const mutate = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()

    // sets up the admin user with default values
    setupUsers()
    mockedUseChangeUserRole.mockReturnValue(defaultMutation as unknown as ReturnType<typeof useChangeUserRole>,)
    mockedUseSetUserActive.mockReturnValue(defaultMutation as unknown as ReturnType<typeof useSetUserActive>,)
    mockedUseCreateAdminUser.mockReturnValue({
      mutate,
      isPending: false,
      isError: false,
      error: null,
      reset: vi.fn(),
    } as unknown as ReturnType<typeof useCreateAdminUser>)
  })

  // opens the create user modal
  function openModal() {
    render(<UserManagementPage />)
    fireEvent.click(screen.getByRole('button', { name: 'Create User' }))
  }

  // validates disabled submission when valid values are not provided
  it('validates the create user form', async () => {
    openModal()
    const createButton = screen.getByRole('button', { name: 'Create Account', })

    // required fields are initially empty
    expect(createButton).toBeDisabled()

    // fill form with valid values
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'John' }, })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'john@example.com' }, })

    // button is enabled with valid values
    await waitFor(() => expect(createButton).toBeEnabled())
  })

  // validate required fields for submission
  it('blocks submission when required fields are empty', () => {
    openModal()
    expect(screen.getByRole('button', { name: 'Create Account' }),).toBeDisabled()
    expect(mutate).not.toHaveBeenCalled()
  })

  // validate display name length
  it('rejects a display name under 2 characters', async () => {
    openModal()
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'J' }, })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'john@example.com' }, })

    await waitFor(() =>expect(screen.getByText('Name must be at least 2 characters'),).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Create Account' }),).toBeDisabled()
    expect(mutate).not.toHaveBeenCalled()
  })

  // validate display name with whitespace-only values
  it('rejects a display name with only whitespace', async () => {
    openModal()
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: '  ' }, })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'john@example.com' }, })

    await waitFor(() =>expect(screen.getByText('Name must be at least 2 characters'),).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Create Account' }),).toBeDisabled()
    expect(mutate).not.toHaveBeenCalled()
  })

  // validate email
  it('rejects an invalid email', async () => {
    openModal()
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'John Doe' }, })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'not an email' }, })

    await waitFor(() =>expect(screen.getByText('Enter a valid email'),).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Create Account' }),).toBeDisabled()
    expect(mutate).not.toHaveBeenCalled()
  })

  // role selection exposes only supported roles, so no invalid role can be submitted
  it('provides only supported role options', () => {
    openModal()
    const roleSelect = screen.getByLabelText('Role')
    const options = Array.from(roleSelect.querySelectorAll('option'),).map((option) => option.value)

    expect(options).toEqual([
      'LEARNER',
      'SCENARIO_AUTHOR',
      'REVIEWER',
      'ADMINISTRATOR',
    ])
  })

  // test create new user flow
  it('submits the create user form with the selected role', async () => {
    openModal()
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'John' }, })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'john@example.com' }, })
    fireEvent.change(screen.getByLabelText('Role'), { target: { value: 'ADMINISTRATOR' }, })
    
    await waitFor(() => expect(screen.getByRole('button', { name: 'Create Account' }),).toBeEnabled())
    fireEvent.click(screen.getByRole('button', { name: 'Create Account' }),)

    // verify that the mutate function was called with the correct data
    await waitFor(() => {
      expect(mutate).toHaveBeenCalledWith(
        {
          displayName: 'John',
          email: 'john@example.com',
          role: 'ADMINISTRATOR',
        },
        expect.anything(),
      )
    })
  })

  // test successful user creation
  it('shows a success notification after creating a user', async () => {
    // mocks the mutate function to call onSuccess
    mockedUseCreateAdminUser.mockReturnValue({
      mutate: vi.fn((_data, options) => {
        options?.onSuccess?.()
      }),
      isPending: false,
      isError: false,
      error: null,
      reset: vi.fn(),
    } as unknown as ReturnType<typeof useCreateAdminUser>)
    openModal()

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'John' }, })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'john@example.com' }, })

    // submit the form
    await waitFor(() => expect(screen.getByRole('button', { name: 'Create Account' }),).toBeEnabled(),)
    fireEvent.click(screen.getByRole('button', { name: 'Create Account' }),)

    // expects success notification after the mutation succeeds
    await waitFor(() => expect(screen.getByText('Account created')).toBeInTheDocument(),)
    expect(screen.getByText('The user account was created and an account setup email has been sent.',),).toBeInTheDocument()
  })

  // tests duplicate email response error handling
  it('shows a specific error when the email already exists', () => {
    // simulate an API error when the email is already registered
    mockedUseCreateAdminUser.mockReturnValue({
      mutate,
      isPending: false,
      isError: true,
      error: {
        isAxiosError: true,
        response: {
          data: {
            detail: 'Email already exists',
          },
        },
      },
      reset: vi.fn(),
    } as unknown as ReturnType<typeof useCreateAdminUser>)
    openModal()

    expect(screen.getByText('Email already in use'),).toBeInTheDocument()
    expect(screen.getByText('An account with this email already exists.',),).toBeInTheDocument()
    expect(screen.queryByText("Couldn't create the account"),).not.toBeInTheDocument()
  })

  // tests generic failure response error handling
  it('shows a generic error for other API failures', () => {
    // simulate an API error for a generic failure (not duplicate email)
    mockedUseCreateAdminUser.mockReturnValue({
      mutate,
      isPending: false,
      isError: true,
      error: new Error('network down'),
      reset: vi.fn(),
    } as unknown as ReturnType<typeof useCreateAdminUser>)
    openModal()

    expect(screen.getByText("Couldn't create the account"),).toBeInTheDocument()
    expect(screen.getByText('Something went wrong. Please try again.'),).toBeInTheDocument()
    expect(screen.queryByText('Email already in use'),).not.toBeInTheDocument()
  })

  // test duplicate submission
  it('prevents duplicate submission while account creation is pending', () => {
    // simulate a pending state for the create user mutation
    mockedUseCreateAdminUser.mockReturnValue({
      mutate,
      isPending: true,
      isError: false,
      error: null,
      reset: vi.fn(),
    } as unknown as ReturnType<typeof useCreateAdminUser>)
    openModal()

    expect(screen.getByRole('button', { name: 'Creating...' }),).toBeDisabled()
    expect(mutate).not.toHaveBeenCalled()
  })
})

describe('UserManagementPage - state rendering', () => {
  beforeEach(() => {
    vi.clearAllMocks()

    // sets up mutations with default values
    mockedUseChangeUserRole.mockReturnValue(defaultMutation as unknown as ReturnType<typeof useChangeUserRole>,)
    mockedUseSetUserActive.mockReturnValue(defaultMutation as unknown as ReturnType<typeof useSetUserActive>,)
    mockedUseCreateAdminUser.mockReturnValue(defaultMutation as unknown as ReturnType<typeof useCreateAdminUser>,)
  })

  // tests the initial loading state
  it('shows the loading state while users are being loaded', () => {
    setupUsers({ isLoading: true })
    render(<UserManagementPage />)

    expect(screen.getByText('Loading…')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.queryByText('People and access')).not.toBeInTheDocument()
  })

  // tests the error state 
  it('shows the error state when loading users fails', () => {
    setupUsers({ isLoading: false, isError: true, })
    render(<UserManagementPage />)

    expect(screen.getByText('Something went wrong')).toBeInTheDocument()
    expect(screen.getByText('Please try refreshing the page.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  // tests the empty state
  it('shows the empty state when there are no registered users', () => {
    setupUsers({ data: [] })
    render(<UserManagementPage />)

    expect(screen.getByText('No users found')).toBeInTheDocument()
    expect(screen.getByText('Registered accounts will appear here.'),).toBeInTheDocument()
    expect(screen.getByText('0 active accounts'),).toBeInTheDocument()
    expect(screen.getByText('across 0 registered users'),).toBeInTheDocument()
    expect(screen.getByRole('table')).toBeInTheDocument()
  })

  // tests the populated state with active and inactive users
  it('renders users and displays the correct active account count', () => {
    setupUsers({
      data: [
        makeUser({id: '01', displayName: 'John', active: true,}),
        makeUser({id: '02', displayName: 'Jane', active: false,}),
        makeUser({id: '03', displayName: 'James', active: true,}),
      ],
    })
    render(<UserManagementPage />)

    expect(screen.queryByText('No users found')).not.toBeInTheDocument()
    expect(screen.getByText('2 active accounts'),).toBeInTheDocument()
    expect(screen.getByText('across 3 registered users'),).toBeInTheDocument()
    expect(screen.getByText('John')).toBeInTheDocument()
    expect(screen.getByText('Jane')).toBeInTheDocument()
    expect(screen.getByText('James')).toBeInTheDocument()
    expect(screen.getAllByText('Active')).toHaveLength(2)
    expect(screen.getAllByText('Inactive')).toHaveLength(1)
  })

  // tests the background refresh state while users are already displayed
  it('shows the refreshing indicator while users are being refreshed', () => {
    setupUsers({data: [makeUser()], isFetching: true,})
    render(<UserManagementPage />)

    expect(screen.getByText('Refreshing users')).toBeInTheDocument()
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getByText('John')).toBeInTheDocument()
  })
})
