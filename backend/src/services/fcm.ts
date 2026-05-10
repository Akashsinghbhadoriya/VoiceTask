import admin from 'firebase-admin';
import { getEnv } from '../config/env.js';

let adminApp: admin.app.App | null = null;

function getFirebaseApp(): admin.app.App {
  if (!adminApp) {
    const env = getEnv();
    const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);

    // Validate required fields
    if (!serviceAccount.project_id || !serviceAccount.private_key || !serviceAccount.client_email) {
      throw new Error('Invalid Firebase service account credentials: missing required fields');
    }

    console.log(`Initializing Firebase Admin SDK for project: ${serviceAccount.project_id}`);

    adminApp = admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
    });
  }
  return adminApp;
}

function getValidTokens(devices: any[]): string[] {
  return devices
    .map((d) => d.fcm_token)
    .filter((token) => token && typeof token === 'string' && token.trim().length > 0);
}

async function cleanupStaleTokens(
  supabase: any,
  fcmTokens: string[],
  responses: admin.messaging.SendResponse[]
): Promise<void> {
  if (!responses.some((r) => !r.success)) return;

  for (let idx = 0; idx < responses.length; idx++) {
    const r = responses[idx];
    if (!r.success && r.error?.code === 'messaging/registration-token-not-registered') {
      await supabase.from('devices').delete().eq('fcm_token', fcmTokens[idx]);
    }
  }
}

export async function sendTaskReminderNotifications(
  devices: any[],
  task: any,
  supabase: any
): Promise<void> {
  if (devices.length === 0) return;

  const app = getFirebaseApp();
  const messaging = admin.messaging(app);

  const fcmTokens = getValidTokens(devices);
  if (fcmTokens.length === 0) {
    console.log('No valid FCM tokens found for devices');
    return;
  }

  // Data-only message (no notification field) so onMessageReceived is always called
  // regardless of app state. The Android app handles the notification display and
  // TTS announcement itself, with deduplication against the local AlarmManager alarm.
  try {
    const response = await messaging.sendEachForMulticast({
      tokens: fcmTokens,
      data: {
        type: 'REMINDER_FIRED',
        taskId: task.id,
        title: task.title,
      },
      android: { priority: 'high' },
    });

    await cleanupStaleTokens(supabase, fcmTokens, response.responses);
  } catch (error) {
    console.error('Failed to send FCM notifications:', {
      errorMessage: error instanceof Error ? error.message : String(error),
      tokenCount: fcmTokens.length,
      deviceCount: devices.length,
    });
    throw error;
  }
}

export async function sendScheduleAlarmMessage(
  devices: any[],
  task: { id: string; title: string; due_at: string; reminder_offset_minutes: number },
  supabase: any
): Promise<void> {
  if (devices.length === 0 || !task.due_at) return;

  const app = getFirebaseApp();
  const messaging = admin.messaging(app);

  const fcmTokens = getValidTokens(devices);
  if (fcmTokens.length === 0) return;

  // Supabase TIMESTAMP columns return without timezone suffix (e.g. "2026-05-10T17:30:00").
  // Appending 'Z' forces UTC parsing — without it, new Date() on a non-UTC machine
  // treats the string as local time and the fireAt ends up hours off.
  const dueAtUtc = task.due_at.endsWith('Z') || task.due_at.includes('+') ? task.due_at : task.due_at + 'Z';
  const fireAtMs = new Date(dueAtUtc).getTime() - task.reminder_offset_minutes * 60_000;

  try {
    const response = await messaging.sendEachForMulticast({
      tokens: fcmTokens,
      data: {
        type: 'SCHEDULE_ALARM',
        taskId: task.id,
        fireAt: fireAtMs.toString(),
        title: task.title,
        reminderOffsetMinutes: task.reminder_offset_minutes.toString(),
      },
      android: { priority: 'high' },
    });

    await cleanupStaleTokens(supabase, fcmTokens, response.responses);
  } catch (error) {
    console.error('Failed to send SCHEDULE_ALARM FCM message:', {
      errorMessage: error instanceof Error ? error.message : String(error),
      taskId: task.id,
    });
    // Don't rethrow — scheduling message failure is non-critical (QStash is the fallback)
  }
}

export async function sendCancelAlarmMessage(
  devices: any[],
  taskId: string
): Promise<void> {
  if (devices.length === 0) return;

  const app = getFirebaseApp();
  const messaging = admin.messaging(app);

  const fcmTokens = getValidTokens(devices);
  if (fcmTokens.length === 0) return;

  try {
    await messaging.sendEachForMulticast({
      tokens: fcmTokens,
      data: {
        type: 'CANCEL_ALARM',
        taskId,
      },
      android: { priority: 'high' },
    });
  } catch (error) {
    console.error('Failed to send CANCEL_ALARM FCM message:', {
      errorMessage: error instanceof Error ? error.message : String(error),
      taskId,
    });
    // Don't rethrow — cancel message failure is non-critical
  }
}
