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

export async function extractTaskDetails(
  text: string,
  timezone: string
): Promise<ExtractedTask> {
  const client = getOpenAiClient();

  const currentTime = new Date().toISOString();

  const systemPrompt = `You are a task extraction assistant. Extract structured task details from the user's text and respond with ONLY a JSON object.

Current time: ${currentTime}
User timezone: ${timezone}

Rules:
- Resolve relative time phrases (today, tomorrow, next Monday, in 2 hours, this evening) to absolute ISO 8601 timestamps in UTC.
- "Evening" = 6 PM, "morning" = 9 AM, "afternoon" = 2 PM, "night" = 9 PM (in user's timezone, then convert to UTC).
- If no time is mentioned at all, set dueAt to null.
- Default reminderOffsetMinutes to 15 unless user specifies otherwise (e.g., "remind me 1 hour before" = 60).
- Default priority to "medium" unless urgency is clearly stated ("urgent", "asap" = high; "whenever", "low priority" = low).
- Title should be short (under 60 chars), imperative ("Call mom", "Submit report").
- Description is for any extra context the user provided beyond the title.

Respond with JSON only, no other text: {"title":"...","description":"...","dueAt":"...","reminderOffsetMinutes":15,"priority":"medium"}`;

  const response = await client.chat.completions.create({
    model: 'gpt-4o-mini',
    max_tokens: 500,
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
