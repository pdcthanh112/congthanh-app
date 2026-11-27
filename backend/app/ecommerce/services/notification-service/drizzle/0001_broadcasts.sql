CREATE TYPE "notification_service"."broadcast_status" AS ENUM('SCHEDULED', 'EXPANDING', 'SENDING', 'COMPLETED', 'CANCELLED');--> statement-breakpoint
CREATE TABLE "notification_service"."broadcasts" (
	"id" bigint PRIMARY KEY NOT NULL,
	"category" varchar(20) NOT NULL,
	"title" varchar(200) NOT NULL,
	"body" text NOT NULL,
	"link" text,
	"channels" "notification_service"."channel"[] NOT NULL,
	"audience" jsonb NOT NULL,
	"status" "notification_service"."broadcast_status" DEFAULT 'SCHEDULED' NOT NULL,
	"scheduled_at" timestamp with time zone NOT NULL,
	"audience_cursor" integer DEFAULT 0 NOT NULL,
	"recipients" integer DEFAULT 0 NOT NULL,
	"lease_until" timestamp with time zone,
	"created_by" text NOT NULL,
	"idempotency_key" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"completed_at" timestamp with time zone,
	"cancelled_at" timestamp with time zone,
	CONSTRAINT "broadcasts_category_check" CHECK (category IN ('PROMOTION', 'SYSTEM')),
	CONSTRAINT "broadcasts_title_check" CHECK (length(btrim(title)) > 0),
	CONSTRAINT "broadcasts_body_check" CHECK (length(btrim(body)) > 0),
	CONSTRAINT "broadcasts_channels_check" CHECK (cardinality(channels) > 0),
	CONSTRAINT "broadcasts_cursor_check" CHECK (audience_cursor >= 0 AND recipients >= 0)
);
--> statement-breakpoint
DROP INDEX "notification_service"."deliveries_due_idx";--> statement-breakpoint
ALTER TABLE "notification_service"."deliveries" ADD COLUMN "priority" smallint DEFAULT 5 NOT NULL;--> statement-breakpoint
ALTER TABLE "notification_service"."deliveries" ADD COLUMN "broadcast_id" bigint;--> statement-breakpoint
CREATE UNIQUE INDEX "broadcasts_idempotency_key" ON "notification_service"."broadcasts" USING btree ("created_by","idempotency_key");--> statement-breakpoint
CREATE INDEX "broadcasts_due_idx" ON "notification_service"."broadcasts" USING btree ("status","scheduled_at");--> statement-breakpoint
ALTER TABLE "notification_service"."deliveries" ADD CONSTRAINT "deliveries_broadcast_id_broadcasts_id_fk" FOREIGN KEY ("broadcast_id") REFERENCES "notification_service"."broadcasts"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
CREATE UNIQUE INDEX "deliveries_notification_channel_key" ON "notification_service"."deliveries" USING btree ("notification_id","channel");--> statement-breakpoint
CREATE INDEX "deliveries_broadcast_idx" ON "notification_service"."deliveries" USING btree ("broadcast_id","status") WHERE broadcast_id IS NOT NULL;--> statement-breakpoint
CREATE INDEX "deliveries_due_idx" ON "notification_service"."deliveries" USING btree ("channel","priority","next_attempt_at") WHERE status = 'PENDING';--> statement-breakpoint
ALTER TABLE "notification_service"."deliveries" ADD CONSTRAINT "deliveries_priority_check" CHECK (priority BETWEEN 0 AND 9);