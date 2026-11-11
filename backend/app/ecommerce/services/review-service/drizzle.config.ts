import { defineConfig } from "drizzle-kit";

export default defineConfig({
  dialect: "postgresql",
  schema: "./src/adapters/out/persistence/schema.ts",
  out: "./drizzle",
  casing: "snake_case",
  schemaFilter: ["review_service"],
});
