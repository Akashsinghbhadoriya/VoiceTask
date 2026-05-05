import { z } from 'zod';

export const prioritySchema = z.enum(['LOW', 'MEDIUM', 'HIGH']);
export const taskStatusSchema = z.enum(['PENDING', 'COMPLETED', 'DISMISSED']);

export const createTaskSchema = z.object({
  title: z.string().min(1).max(255),
  description: z.string().optional(),
  dueAt: z.string().datetime().nullable().optional(),
  reminderOffsetMinutes: z.number().int().nonnegative().default(15),
  priority: prioritySchema.default('MEDIUM'),
  transcript: z.string().optional(),
});

export const updateTaskSchema = z.object({
  title: z.string().min(1).max(255).optional(),
  description: z.string().optional(),
  dueAt: z.string().datetime().nullable().optional(),
  reminderOffsetMinutes: z.number().int().nonnegative().optional(),
  priority: prioritySchema.optional(),
  status: taskStatusSchema.optional(),
  transcript: z.string().optional(),
});

export const taskResponseSchema = z.object({
  id: z.string(),
  userId: z.string(),
  title: z.string(),
  description: z.string().nullable(),
  dueAt: z.string().datetime().nullable(),
  reminderOffsetMinutes: z.number(),
  priority: prioritySchema,
  status: taskStatusSchema,
  transcript: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const transcribeResponseSchema = z.object({
  transcript: z.string(),
});

export const extractTaskSchema = z.object({
  text: z.string(),
  timezone: z.string(),
});

export const extractedTaskSchema = z.object({
  title: z.string(),
  description: z.string().nullable(),
  dueAt: z.string().datetime().nullable(),
  reminderOffsetMinutes: z.number(),
  priority: z.enum(['low', 'medium', 'high']),
});

export type CreateTaskInput = z.infer<typeof createTaskSchema>;
export type UpdateTaskInput = z.infer<typeof updateTaskSchema>;
export type TaskResponse = z.infer<typeof taskResponseSchema>;
export type TranscribeResponse = z.infer<typeof transcribeResponseSchema>;
export type ExtractTaskInput = z.infer<typeof extractTaskSchema>;
export type ExtractedTask = z.infer<typeof extractedTaskSchema>;
