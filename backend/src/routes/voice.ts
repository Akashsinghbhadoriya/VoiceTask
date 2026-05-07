import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { authMiddleware } from '../middleware/auth.js';
import { transcribeAudio, extractTaskDetails } from '../services/openai.js';
import { extractTaskSchema, transcribeResponseSchema, extractedTaskSchema } from '../schemas/task.js';
import { BadRequestError } from '../utils/errors.js';
import { getSupabase } from '../config/supabase.js';

// AUDIO IS NEVER PERSISTED — in-memory buffer only, discarded after OpenAI call

async function postTranscribe(request: FastifyRequest, reply: FastifyReply) {
  await authMiddleware(request, reply);

  const data = await request.file();

  if (!data) {
    throw new BadRequestError('No file provided');
  }

  // Read buffer from the multipart stream
  const chunks: Buffer[] = [];
  for await (const chunk of data.file) {
    chunks.push(chunk instanceof Buffer ? chunk : Buffer.from(chunk));
  }

  const buffer = Buffer.concat(chunks);
  const fileSizeKB = buffer.length / 1024;
  const fileSizeMB = fileSizeKB / 1024;

  request.log.info(
    {
      fileSizeBytes: buffer.length,
      fileSizeKB: fileSizeKB.toFixed(2),
      fileSizeMB: fileSizeMB.toFixed(4),
      fileName: data.filename,
      mimeType: data.mimetype,
    },
    'Audio file received'
  );

  // Validate file size (max 10 MB)
  const maxSize = 10 * 1024 * 1024;
  if (buffer.length > maxSize) {
    reply.status(413).send({ error: 'File too large', code: 'PAYLOAD_TOO_LARGE' });
    return;
  }

  try {
    const transcript = await transcribeAudio(buffer);

    // Discard buffer after OpenAI call
    const result = transcribeResponseSchema.parse({ transcript });

    reply.status(200).send(result);
  } catch (error) {
    request.log.error(error, 'Transcription error');
    throw new BadRequestError(
      `Transcription failed: ${error instanceof Error ? error.message : String(error)}`
    );
  }
}

async function postExtract(request: FastifyRequest, reply: FastifyReply) {
  await authMiddleware(request, reply);

  const bodyResult = extractTaskSchema.safeParse(request.body);
  if (!bodyResult.success) {
    throw new BadRequestError('Invalid request body');
  }

  const { text, timezone: clientProvidedTimezone } = bodyResult.data;
  const userId = request.user!.id;

  try {
    // Fetch user's timezone from database
    let userTimezone = clientProvidedTimezone;

    if (!userTimezone) {
      const supabase = getSupabase();
      const { data: user, error } = (await supabase
        .from('users')
        .select('timezone')
        .eq('id', userId)
        .single()) as any;

      if (error) {
        request.log.warn({ error }, 'Failed to fetch user timezone, using default');
      }

      userTimezone = user?.timezone || 'Asia/Kolkata'; // Default to India timezone
    }

    const extracted = await extractTaskDetails(text, userTimezone);
    const result = extractedTaskSchema.parse(extracted);

    reply.status(200).send(result);
  } catch (error) {
    request.log.error(error, 'Task extraction error');
    throw new BadRequestError(
      `Extraction failed: ${error instanceof Error ? error.message : String(error)}`
    );
  }
}

export async function registerVoiceRoutes(fastify: FastifyInstance) {
  fastify.post('/voice/transcribe', postTranscribe);
  fastify.post('/voice/extract', postExtract);
}
