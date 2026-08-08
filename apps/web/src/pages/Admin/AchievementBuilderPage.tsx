import { useState } from 'react'
import {
  Grid,
  Column,
  Heading,
  Stack,
  Tile,
  Button,
  TextInput,
  TextArea,
  Tag,
  InlineNotification,
} from '@carbon/react'
import { Add, CheckmarkFilled, CloseFilled } from '@carbon/icons-react'
import {
  useAdminAchievements,
  useCreateAchievement,
  useSetAchievementActive,
  useUpdateAchievement,
} from '@/api/hooks/useAdminAchievements'
import AchievementRuleBuilder from '@/components/admin/AchievementRuleBuilder'
import LoadingState from '@/components/shared/LoadingState'
import ErrorState from '@/components/shared/ErrorState'
import type { AchievementAdminView, ConditionNode, UpsertAchievementRequest } from '@/api/types'

const DEFAULT_RULE: ConditionNode = {
  kind: 'GROUP',
  operator: 'AND',
  children: [
    { kind: 'LEAF', operator: null, children: null, type: 'MIN_ENGAGEMENTS_COMPLETED', competencyName: null, threshold: 1 },
  ],
  type: null,
  competencyName: null,
  threshold: null,
}

function emptyForm(): UpsertAchievementRequest {
  return { name: '', description: '', iconKey: 'trophy', rule: DEFAULT_RULE }
}

function AchievementForm({
  initial,
  onSubmit,
  onCancel,
  submitting,
}: {
  initial: UpsertAchievementRequest
  onSubmit: (request: UpsertAchievementRequest) => void
  onCancel: () => void
  submitting: boolean
}) {
  const [form, setForm] = useState<UpsertAchievementRequest>(initial)

  return (
    <Tile>
      <Stack gap={5}>
        <TextInput
          id="achievement-name"
          labelText="Achievement name"
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
        />
        <TextArea
          id="achievement-description"
          labelText="Description"
          value={form.description}
          onChange={(e) => setForm({ ...form, description: e.target.value })}
        />
        <TextInput
          id="achievement-icon"
          labelText="Icon key"
          helperText="Identifier used by the frontend badge renderer (e.g. trophy, star, medal)"
          value={form.iconKey}
          onChange={(e) => setForm({ ...form, iconKey: e.target.value })}
        />

        <div>
          <h5 style={{ marginBottom: '0.75rem' }}>Unlock rule</h5>
          <AchievementRuleBuilder node={form.rule} onChange={(rule) => setForm({ ...form, rule })} />
        </div>

        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <Button
            onClick={() => onSubmit(form)}
            disabled={!form.name.trim() || !form.description.trim() || submitting}
          >
            Save achievement
          </Button>
          <Button kind="ghost" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </Stack>
    </Tile>
  )
}

function AchievementRow({ achievement }: { achievement: AchievementAdminView }) {
  const [editing, setEditing] = useState(false)
  const updateAchievement = useUpdateAchievement(achievement.id)
  const setActive = useSetAchievementActive()

  if (editing) {
    return (
      <AchievementForm
        initial={{
          name: achievement.name,
          description: achievement.description,
          iconKey: achievement.iconKey,
          rule: achievement.rule,
        }}
        submitting={updateAchievement.isPending}
        onCancel={() => setEditing(false)}
        onSubmit={(request) => updateAchievement.mutate(request, { onSuccess: () => setEditing(false) })}
      />
    )
  }

  return (
    <Tile>
      <Stack gap={3}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <h5>{achievement.name}</h5>
            <p style={{ color: '#525252', fontSize: '0.875rem' }}>{achievement.description}</p>
          </div>
          <Tag type={achievement.active ? 'green' : 'gray'}>{achievement.active ? 'Active' : 'Inactive'}</Tag>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <Button kind="tertiary" size="sm" onClick={() => setEditing(true)}>
            Edit rule
          </Button>
          <Button
            kind={achievement.active ? 'danger--tertiary' : 'tertiary'}
            size="sm"
            renderIcon={achievement.active ? CloseFilled : CheckmarkFilled}
            onClick={() => setActive.mutate({ id: achievement.id, active: !achievement.active })}
            disabled={setActive.isPending}
          >
            {achievement.active ? 'Deactivate' : 'Activate'}
          </Button>
        </div>
      </Stack>
    </Tile>
  )
}

export default function AchievementBuilderPage() {
  const { data: achievements, isLoading, isError } = useAdminAchievements()
  const createAchievement = useCreateAchievement()
  const [creating, setCreating] = useState(false)

  if (isLoading) return <LoadingState />
  if (isError) return <ErrorState />

  return (
    <Grid fullWidth style={{ padding: '2rem' }}>
      <Column lg={16} md={8} sm={4}>
        <Stack gap={6}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <Heading>Achievement Builder</Heading>
              <p style={{ color: '#525252', marginTop: '0.5rem' }}>
                Design gamification badges with nested AND/OR unlock rules — fully customisable
                without a deployment.
              </p>
            </div>
            {!creating && (
              <Button renderIcon={Add} onClick={() => setCreating(true)}>
                New achievement
              </Button>
            )}
          </div>

          {createAchievement.isError && (
            <InlineNotification kind="error" title="Could not save achievement" subtitle="Check the rule tree and try again." />
          )}

          {creating && (
            <AchievementForm
              initial={emptyForm()}
              submitting={createAchievement.isPending}
              onCancel={() => setCreating(false)}
              onSubmit={(request) => createAchievement.mutate(request, { onSuccess: () => setCreating(false) })}
            />
          )}

          {(achievements ?? []).length === 0 && !creating ? (
            <Tile>
              <p style={{ color: '#525252' }}>No achievements defined yet. Create the first one above.</p>
            </Tile>
          ) : (
            <Stack gap={4}>
              {(achievements ?? []).map((a) => (
                <AchievementRow key={a.id} achievement={a} />
              ))}
            </Stack>
          )}
        </Stack>
      </Column>
    </Grid>
  )
}
