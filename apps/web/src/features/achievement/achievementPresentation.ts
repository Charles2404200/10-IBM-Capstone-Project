const MISSING_ACHIEVEMENT_DESCRIPTION = 'No description provided.'

export function achievementDescription(description: string | null): string {
  return description?.trim() ? description : MISSING_ACHIEVEMENT_DESCRIPTION
}
