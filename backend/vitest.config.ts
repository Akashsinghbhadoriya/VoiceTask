import { defineConfig } from 'vitest/config';
import { config } from 'dotenv';

config(); // Load .env before tests run

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    testTimeout: 45000, // Whisper API calls can take ~5–10 s
  },
});
