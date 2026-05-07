import { Client } from '@upstash/qstash';
import { getEnv } from '../config/env.js';

let qstashClient: Client | null = null;

function getQstashClient(): Client {
  if (!qstashClient) {
    const env = getEnv();
    qstashClient = new Client({ token: env.QSTASH_TOKEN, baseUrl: env.QSTASH_URL });
  }
  return qstashClient;
}

export async function scheduleTaskReminder(
  taskId: string,
  fireAt: Date
): Promise<string> {
  const client = getQstashClient();
  const env = getEnv();

  // Skip if fire time is in the past
  const now = new Date();
  if (fireAt <= now) {
    console.warn(
      `Task ${taskId} reminder time is in the past, skipping scheduling`
    );
    return '';
  }

  const response = await client.publishJSON({
    url: `${env.BACKEND_PUBLIC_URL}/webhooks/qstash`,
    body: { taskId },
    delay: Math.floor((fireAt.getTime() - now.getTime()) / 1000),
  });

  return response.messageId || '';
}

export async function cancelTaskReminder(messageId: string): Promise<void> {
  if (!messageId) return;

  const client = getQstashClient();

  try {
    await (client as any).messages.delete(messageId);
  } catch (error) {
    // Ignore 404 errors (message already processed or doesn't exist)
    if (
      error instanceof Error &&
      error.message.includes('404')
    ) {
      return;
    }
    throw error;
  }
}
