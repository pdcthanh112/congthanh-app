CREATE SCHEMA IF NOT EXISTS "notification_service";
--> statement-breakpoint
CREATE TYPE "notification_service"."channel" AS ENUM('IN_APP', 'EMAIL', 'SMS', 'WEB_PUSH', 'FCM');--> statement-breakpoint
CREATE TYPE "notification_service"."delivery_status" AS ENUM('PENDING', 'SENT', 'FAILED', 'SKIPPED');--> statement-breakpoint
CREATE TABLE "notification_service"."deliveries" (
	"id" text PRIMARY KEY NOT NULL,
	"notification_id" text NOT NULL,
	"channel" "notification_service"."channel" NOT NULL,
	"status" "notification_service"."delivery_status" DEFAULT 'PENDING' NOT NULL,
	"attempts" integer DEFAULT 0 NOT NULL,
	"last_error" text,
	"next_attempt_at" timestamp with time zone DEFAULT now() NOT NULL,
	"sent_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "notification_service"."fcm_tokens" (
	"token" text PRIMARY KEY NOT NULL,
	"user_id" text NOT NULL,
	"platform" varchar(20) NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "notification_service"."notifications" (
	"id" text PRIMARY KEY NOT NULL,
	"user_id" text NOT NULL,
	"category" varchar(20) NOT NULL,
	"type" varchar(40) NOT NULL,
	"title" varchar(200) NOT NULL,
	"body" text NOT NULL,
	"link" text,
	"dedup_key" text NOT NULL,
	"read_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "notification_service"."order_customers" (
	"order_id" text PRIMARY KEY NOT NULL,
	"customer_id" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "notification_service"."outbox" (
	"id" uuid PRIMARY KEY NOT NULL,
	"aggregatetype" text NOT NULL,
	"aggregateid" text NOT NULL,
	"type" text NOT NULL,
	"payload" "bytea" NOT NULL,
	"traceparent" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "notification_service"."preferences" (
	"user_id" text PRIMARY KEY NOT NULL,
	"disabled" text[] NOT NULL,
	"quiet_hours" jsonb,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "notification_service"."processed_events" (
	"event_id" text NOT NULL,
	"consumer" text NOT NULL,
	"processed_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "processed_events_event_id_consumer_pk" PRIMARY KEY("event_id","consumer")
);
--> statement-breakpoint
CREATE TABLE "notification_service"."product_states" (
	"product_id" text PRIMARY KEY NOT NULL,
	"name" text,
	"price_minor" bigint,
	"currency" varchar(3),
	"available" integer,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "notification_service"."product_watchers" (
	"product_id" text NOT NULL,
	"customer_id" text NOT NULL,
	CONSTRAINT "product_watchers_product_id_customer_id_pk" PRIMARY KEY("product_id","customer_id")
);
--> statement-breakpoint
CREATE TABLE "notification_service"."web_push_subscriptions" (
	"endpoint" text PRIMARY KEY NOT NULL,
	"user_id" text NOT NULL,
	"p256dh" text NOT NULL,
	"auth" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "notification_service"."deliveries" ADD CONSTRAINT "deliveries_notification_id_notifications_id_fk" FOREIGN KEY ("notification_id") REFERENCES "notification_service"."notifications"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "deliveries_due_idx" ON "notification_service"."deliveries" USING btree ("status","next_attempt_at");--> statement-breakpoint
CREATE INDEX "fcm_tokens_user_idx" ON "notification_service"."fcm_tokens" USING btree ("user_id");--> statement-breakpoint
CREATE UNIQUE INDEX "notifications_dedup_key" ON "notification_service"."notifications" USING btree ("dedup_key");--> statement-breakpoint
CREATE INDEX "notifications_user_recent_idx" ON "notification_service"."notifications" USING btree ("user_id","created_at");--> statement-breakpoint
CREATE INDEX "web_push_user_idx" ON "notification_service"."web_push_subscriptions" USING btree ("user_id");