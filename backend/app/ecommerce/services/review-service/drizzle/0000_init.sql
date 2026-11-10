CREATE SCHEMA IF NOT EXISTS "review_service";
--> statement-breakpoint
CREATE TYPE "review_service"."purchase_status" AS ENUM('PENDING', 'CONFIRMED', 'CANCELLED');--> statement-breakpoint
CREATE TYPE "review_service"."review_status" AS ENUM('PUBLISHED', 'HIDDEN');--> statement-breakpoint
CREATE TABLE "review_service"."helpful_votes" (
	"review_id" text NOT NULL,
	"user_id" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "helpful_votes_review_id_user_id_pk" PRIMARY KEY("review_id","user_id")
);
--> statement-breakpoint
CREATE TABLE "review_service"."outbox" (
	"id" uuid PRIMARY KEY NOT NULL,
	"aggregatetype" text NOT NULL,
	"aggregateid" text NOT NULL,
	"type" text NOT NULL,
	"payload" "bytea" NOT NULL,
	"traceparent" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "review_service"."processed_events" (
	"event_id" text NOT NULL,
	"consumer" text NOT NULL,
	"processed_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "processed_events_event_id_consumer_pk" PRIMARY KEY("event_id","consumer")
);
--> statement-breakpoint
CREATE TABLE "review_service"."purchases" (
	"order_id" text NOT NULL,
	"product_id" text NOT NULL,
	"customer_id" text NOT NULL,
	"status" "review_service"."purchase_status" DEFAULT 'PENDING' NOT NULL,
	"confirmed_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "purchases_order_id_product_id_pk" PRIMARY KEY("order_id","product_id")
);
--> statement-breakpoint
CREATE TABLE "review_service"."rating_summaries" (
	"product_id" text PRIMARY KEY NOT NULL,
	"star1" bigint DEFAULT 0 NOT NULL,
	"star2" bigint DEFAULT 0 NOT NULL,
	"star3" bigint DEFAULT 0 NOT NULL,
	"star4" bigint DEFAULT 0 NOT NULL,
	"star5" bigint DEFAULT 0 NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "review_service"."reviews" (
	"id" text PRIMARY KEY NOT NULL,
	"product_id" text NOT NULL,
	"customer_id" text NOT NULL,
	"author_name" varchar(120) NOT NULL,
	"order_id" text NOT NULL,
	"rating" smallint NOT NULL,
	"title" varchar(150),
	"content" text,
	"media_urls" text[] DEFAULT '{}'::text[] NOT NULL,
	"status" "review_service"."review_status" DEFAULT 'PUBLISHED' NOT NULL,
	"moderation_reason" text,
	"helpful_count" integer DEFAULT 0 NOT NULL,
	"seller_reply" text,
	"replied_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	"version" integer DEFAULT 0 NOT NULL,
	CONSTRAINT "reviews_rating_range" CHECK ("review_service"."reviews"."rating" between 1 and 5)
);
--> statement-breakpoint
ALTER TABLE "review_service"."helpful_votes" ADD CONSTRAINT "helpful_votes_review_id_reviews_id_fk" FOREIGN KEY ("review_id") REFERENCES "review_service"."reviews"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "purchases_customer_idx" ON "review_service"."purchases" USING btree ("customer_id","status");--> statement-breakpoint
CREATE UNIQUE INDEX "reviews_one_per_purchase" ON "review_service"."reviews" USING btree ("customer_id","order_id","product_id");--> statement-breakpoint
CREATE INDEX "reviews_product_recent_idx" ON "review_service"."reviews" USING btree ("product_id","status","created_at");--> statement-breakpoint
CREATE INDEX "reviews_product_helpful_idx" ON "review_service"."reviews" USING btree ("product_id","status","helpful_count");--> statement-breakpoint
CREATE INDEX "reviews_customer_idx" ON "review_service"."reviews" USING btree ("customer_id");