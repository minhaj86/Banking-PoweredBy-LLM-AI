import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { APP_DESCRIPTION, APP_SLUG, APP_TITLE } from "./src/app/brand.ts";
export default defineConfig({
  plugins: [
    react(),
    {
      name: `${APP_SLUG}-branding`,
      transformIndexHtml: (html) =>
        html
          .replaceAll("__APP_TITLE__", APP_TITLE)
          .replaceAll("__APP_DESCRIPTION__", APP_DESCRIPTION),
    },
  ],
  server: {
    port: 5173,
    proxy: Object.fromEntries(
      ["/bff", "/oauth2", "/login", "/logout", "/auth"].map((path) => [
        path,
        { target: "http://localhost:18082", changeOrigin: false },
      ]),
    ),
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    include: ["src/**/*.test.{ts,tsx}"],
    restoreMocks: true,
    maxWorkers: 2,
    testTimeout: 15000,
  },
  build: { sourcemap: false },
});
