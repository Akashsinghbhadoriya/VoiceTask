import admin from 'firebase-admin';
import { getEnv } from '../config/env.js';

let adminApp: admin.app.App | null = null;

function getFirebaseApp(): admin.app.App {
  if (!adminApp) {
    const env = getEnv();
    const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
    adminApp = admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
    });
  }
  return adminApp;
}

export async function sendTaskReminderNotifications(
  devices: any[],
  task: any,
  supabase: any
): Promise<void> {
  if (devices.length === 0) return;

  const app = getFirebaseApp();
  const messaging = admin.messaging(app);

  const fcmTokens = devices.map((d) => d.fcm_token);

  // Calculate relative time string
  const now = new Date();
  const dueAt = task.due_at ? new Date(task.due_at) : null;
  let dueString = 'Later';

  if (dueAt) {
    const diffMs = dueAt.getTime() - now.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMins < 1) {
      dueString = 'Now';
    } else if (diffMins < 60) {
      dueString = `in ${diffMins}m`;
    } else if (diffHours < 24) {
      dueString = `in ${diffHours}h`;
    } else {
      dueString = `in ${diffDays}d`;
    }
  }

  const notification: admin.messaging.Notification = {
    title: task.title,
    body: `Due ${dueString}`,
  };

  try {
    const response = await messaging.sendMulticast({
      tokens: fcmTokens,
      notification,
      data: {
        taskId: task.id,
      },
    });

    // Delete any invalid tokens
    if (response.failureCount > 0) {
      const failedTokens = response.responses
        .map((r: any, idx: number) => (r.success ? null : fcmTokens[idx]))
        .filter(Boolean) as string[];

      for (const token of failedTokens) {
        const error = response.responses[fcmTokens.indexOf(token)]?.error;
        if (
          error &&
          error.code === 'messaging/registration-token-not-registered'
        ) {
          // Delete the device record for this invalid token
          await supabase
            .from('devices')
            .delete()
            .eq('fcm_token', token)
            .catch(() => {
              // Silently ignore if device doesn't exist
            });
        }
      }
    }
  } catch (error) {
    console.error('Failed to send FCM notifications:', error);
    throw error;
  }
}
