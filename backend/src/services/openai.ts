import { OpenAI } from 'openai';
import { getEnv } from '../config/env.js';
import { ExtractedTask } from '../schemas/task.js';
import { toFile } from 'openai';

let openaiClient: OpenAI | null = null;

function getOpenAiClient(): OpenAI {
  if (!openaiClient) {
    const env = getEnv();
    openaiClient = new OpenAI({ apiKey: env.OPENAI_API_KEY });
  }
  return openaiClient;
}

export async function transcribeAudio(buffer: Buffer): Promise<string> {
  const client = getOpenAiClient();

  // Convert buffer to File object
  const file = await toFile(buffer, 'audio.m4a', { type: 'audio/mp4' });

  const response = await client.audio.transcriptions.create({
    file: file,
    model: 'whisper-1',
    language: 'en',
  });

  return response.text;
}

/**
 * Convert UTC ISO string to user's timezone as an ISO-like string
 * Returns the wall-clock time in user's timezone formatted as ISO
 * Example: UTC "2026-05-07T04:30:00Z" → IST "2026-05-07T10:30:00Z" (wall clock 10:30 AM)
 */
export function convertUtcToUserTimezone(utcIsoString: string | null, timezone: string): string | null {
  if (!utcIsoString) return null;

  try {
    // Ensure the string ends with Z (for UTC interpretation)
    // Some databases strip the Z when serializing, so we need to add it back
    const normalizedString = utcIsoString.includes('Z') ? utcIsoString : utcIsoString + 'Z';
    const date = new Date(normalizedString);

    const formatter = new Intl.DateTimeFormat('en-US', {
      timeZone: timezone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    });

    const parts = formatter.formatToParts(date);
    const map: { [key: string]: string } = {};
    parts.forEach(part => {
      if (part.type !== 'literal') map[part.type] = part.value;
    });

    // Format as ISO-like string (wall clock time formatted as UTC for display)
    return `${map['year']}-${map['month']}-${map['day']}T${map['hour']}:${map['minute']}:${map['second']}Z`;
  } catch (error) {
    return utcIsoString; // Fallback to original if conversion fails
  }
}

function getTimeInTimezone(timezone: string): { iso: string; readable: string } {
  try {
    const now = new Date();

    // Current UTC time (timezone-independent)
    const iso = now.toISOString();

    // Format the current time in the target timezone
    const formatter = new Intl.DateTimeFormat('en-US', {
      timeZone: timezone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    });

    const parts = formatter.formatToParts(now);
    const map: { [key: string]: string } = {};
    parts.forEach(part => {
      if (part.type !== 'literal') map[part.type] = part.value;
    });

    const readable = `${map['year']}-${map['month']}-${map['day']} ${map['hour']}:${map['minute']}:${map['second']} ${timezone}`;

    return { iso, readable };
  } catch (error) {
    // Fallback to UTC if timezone is invalid
    const now = new Date();
    return { iso: now.toISOString(), readable: now.toISOString() };
  }
}

export async function extractTaskDetails(
  text: string,
  timezone: string = 'Asia/Kolkata' // Default to India timezone
): Promise<ExtractedTask> {
  const client = getOpenAiClient();

  const { iso: currentTimeISO, readable: currentTimeReadable } = getTimeInTimezone(timezone);

  const systemPrompt = `You are an expert task extraction assistant for a voice-to-task application. Your job is to parse natural language task descriptions and extract structured task information. Respond with ONLY a valid JSON object, no other text.

## CURRENT CONTEXT
- Current time (ISO): ${currentTimeISO}
- Current time (readable): ${currentTimeReadable}
- User timezone: ${timezone}

## TASK FIELD DEFINITIONS

### title (required)
- Short, actionable title (max 60 characters)
- Use imperative form: "Call mom", "Submit report", "Buy groceries"
- Should capture the main action, not full details
- If user's input is a question or unclear, make reasonable assumption

### description (nullable)
- Additional context, details, or notes beyond the title
- Include phone numbers, email addresses, specific items, URLs, names
- Should clarify or expand on the title
- Set to null if user only provided a simple task with no extra details

### dueAt (nullable, ISO 8601 UTC)
- When the task should be completed
- Set to null if NO time reference is mentioned at all
- Convert all times to UTC timestamp in ISO 8601 format
- Time resolution: interpret relative times from user's timezone, then convert to UTC

#### Time Interpretation Rules:
- "today" = today at default time (varies by task type):
  - Morning/wake-up tasks: 9:00 AM in user's timezone
  - Work/business tasks: 10:00 AM in user's timezone
  - Evening/personal tasks: 6:00 PM in user's timezone
  - If unclear, use 10:00 AM in user's timezone

- "tomorrow" = tomorrow at 10:00 AM in user's timezone

- "tonight/this evening" = today at 6:00 PM in user's timezone

- "this morning" = today at 9:00 AM in user's timezone

- "this afternoon" = today at 2:00 PM in user's timezone

- "next [day]" (e.g., "next Monday") = the next occurrence of that day at 10:00 AM in user's timezone

- "in X hours/days/weeks" = add X duration from current time, preserving user's timezone

- "tonight" = today at 9:00 PM in user's timezone

- Specific times (e.g., "at 3 PM", "at 15:00") = today at that specific time in user's timezone, UNLESS:
  - User says "tomorrow at 3 PM" → tomorrow at that time
  - User says "next Monday at 3 PM" → next Monday at that time

- Relative days from today (e.g., "in 3 days") = that date at 10:00 AM in user's timezone

- If time format is "3pm" or "3:00 PM" = interpret as 24-hour format (15:00)

#### Edge Cases:
- "end of day" / "EOD" = 5:00 PM in user's timezone
- "first thing" / "ASAP" = 9:00 AM in user's timezone
- "whenever" = null (no specific deadline)
- No time context = null

### reminderOffsetMinutes (required, non-negative integer)
- How many minutes before the dueAt time to remind the user
- Defaults to 15 minutes if not specified
- Examples: "remind me 1 hour before" = 60 minutes, "30 min before" = 30 minutes, "on the day" = 1440 minutes (or similar large value)
- If user says "remind me when it's due" = 0 minutes
- If no reminder preference mentioned, use 15 minutes

### priority (required, enum: "low" | "medium" | "high")
- "high": User says "urgent", "asap", "important", "critical", "emergency", "today", "this hour", "immediately"
- "low": User says "whenever", "someday", "no rush", "low priority", "maybe", "if I have time"
- "medium": Everything else (default)
- If unclear from urgency keywords, consider deadline proximity:
  - Due within 1 hour → high
  - Due within 1 day → medium-high (still medium unless other signals)
  - Due in 1+ week → low-medium (still medium unless other signals)

## JSON OUTPUT FORMAT
{
  "title": "string (max 60 chars, imperative form)",
  "description": "string or null",
  "dueAt": "ISO 8601 UTC timestamp or null",
  "reminderOffsetMinutes": number,
  "priority": "low" | "medium" | "high"
}

## CRITICAL RULES
1. ONLY output valid JSON, no markdown, no code blocks, no explanation
2. All timestamps in dueAt MUST be valid ISO 8601 UTC format
3. Return null for dueAt if absolutely no time reference exists
4. Be conservative: if unsure about a field, use the default or null
5. Parse voice/speech quirks (e.g., "ya" → "you", "gonna" → "going to")
6. Handle list items: if multiple tasks mentioned, extract only the FIRST one as primary task`;

  const response = await client.chat.completions.create({
    model: 'gpt-4o-mini',
    max_tokens: 500,
    temperature: 0.3, // Lower temperature for more consistent extraction
    messages: [
      {
        role: 'system',
        content: systemPrompt,
      },
      {
        role: 'user',
        content: text,
      },
    ],
  });

  const choice = response.choices[0];
  if (choice.message.content === null) {
    throw new Error('Empty response from OpenAI');
  }

  // Parse JSON response
  let parsed: ExtractedTask;
  try {
    // Try to extract JSON from the response
    const jsonMatch = choice.message.content.match(/\{[\s\S]*\}/);
    if (!jsonMatch) {
      throw new Error('No JSON found in response');
    }
    parsed = JSON.parse(jsonMatch[0]);
  } catch (error) {
    throw new Error(`Failed to parse OpenAI response: ${error instanceof Error ? error.message : String(error)}`);
  }

  return {
    title: parsed.title || 'Untitled Task',
    description: parsed.description || null,
    dueAt: parsed.dueAt || null,
    reminderOffsetMinutes: parsed.reminderOffsetMinutes || 15,
    priority: (parsed.priority || 'medium').toLowerCase() as 'low' | 'medium' | 'high',
  };
}
