CREATE SCHEMA IF NOT EXISTS "customer_service";
--> statement-breakpoint
CREATE TYPE "customer_service"."address_label" AS ENUM('HOME', 'OFFICE', 'OTHER');--> statement-breakpoint
CREATE TABLE "customer_service"."outbox" (
	"id" uuid PRIMARY KEY NOT NULL,
	"aggregatetype" text NOT NULL,
	"aggregateid" text NOT NULL,
	"type" text NOT NULL,
	"payload" "bytea" NOT NULL,
	"traceparent" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "customer_service"."processed_events" (
	"event_id" text NOT NULL,
	"consumer" text NOT NULL,
	"processed_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "processed_events_event_id_consumer_pk" PRIMARY KEY("event_id","consumer")
);
--> statement-breakpoint
CREATE TABLE "customer_service"."search_history" (
	"user_id" text NOT NULL,
	"keyword" varchar(200) NOT NULL,
	"searched_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "search_history_user_id_keyword_pk" PRIMARY KEY("user_id","keyword")
);
--> statement-breakpoint
CREATE TABLE "customer_service"."shipping_addresses" (
	"id" text PRIMARY KEY NOT NULL,
	"customer_id" text NOT NULL,
	"full_name" varchar(120) NOT NULL,
	"phone_number" varchar(20) NOT NULL,
	"label" "customer_service"."address_label" DEFAULT 'HOME' NOT NULL,
	"country_code" varchar(2) NOT NULL,
	"province_code" varchar(20),
	"district_code" varchar(20),
	"ward_code" varchar(20),
	"address_line" text NOT NULL,
	"postal_code" varchar(20),
	"latitude" double precision,
	"longitude" double precision,
	"is_default" boolean DEFAULT false NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "customer_service"."wishlist_items" (
	"customer_id" text NOT NULL,
	"product_id" text NOT NULL,
	"added_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "wishlist_items_customer_id_product_id_pk" PRIMARY KEY("customer_id","product_id")
);
--> statement-breakpoint
CREATE INDEX "search_history_recent_idx" ON "customer_service"."search_history" USING btree ("user_id","searched_at");--> statement-breakpoint
CREATE INDEX "shipping_addresses_customer_idx" ON "customer_service"."shipping_addresses" USING btree ("customer_id");--> statement-breakpoint
CREATE UNIQUE INDEX "shipping_addresses_one_default" ON "customer_service"."shipping_addresses" USING btree ("customer_id") WHERE "customer_service"."shipping_addresses"."is_default";--> statement-breakpoint
CREATE INDEX "wishlist_items_recent_idx" ON "customer_service"."wishlist_items" USING btree ("customer_id","added_at");