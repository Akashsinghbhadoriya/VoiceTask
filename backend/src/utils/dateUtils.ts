const IST_FORMAT = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: true,
});

export function toIST(utcString: string | null | undefined): string | null {
  if (!utcString) return null;
  try {
    return IST_FORMAT.format(new Date(utcString));
  } catch {
    return utcString;
  }
}

export function formatTaskDates(task: Record<string, any>): Record<string, any> {
  return {
    ...task,
    due_at: toIST(task.due_at),
    created_at: toIST(task.created_at),
    updated_at: toIST(task.updated_at),
  };
}
