CREATE SCHEMA IF NOT EXISTS "message_service";
--> statement-breakpoint
CREATE TYPE "message_service"."conversation_kind" AS ENUM('SELLER', 'SUPPORT');--> statement-breakpoint
CREATE TABLE "message_service"."conversation_reads" (
	"conversation_id" text NOT NULL,
	"user_id" text NOT NULL,
	"last_read_seq" bigint DEFAULT 0 NOT NULL,
	CONSTRAINT "conversation_reads_conversation_id_user_id_pk" PRIMARY KEY("conversation_id","user_id")
);
--> statement-breakpoint
CREATE TABLE "message_service"."conversations" (
	"id" text PRIMARY KEY NOT NULL,
	"kind" "message_service"."conversation_kind" NOT NULL,
	"customer_id" text NOT NULL,
	"counterpart_id" text NOT NULL,
	"product_id" text,
	"order_id" text,
	"last_seq" bigint DEFAULT 0 NOT NULL,
	"last_message_at" timestamp with time zone,
	"last_message_preview" varchar(120),
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "message_service"."messages" (
	"id" text PRIMARY KEY NOT NULL,
	"conversation_id" text NOT NULL,
	"seq" bigint NOT NULL,
	"sender_id" text NOT NULL,
	"body" text NOT NULL,
	"attachments" text[] NOT NULL,
	"client_message_id" varchar(64),
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "message_service"."outbox" (
	"id" uuid PRIMARY KEY NOT NULL,
	"aggregatetype" text NOT NULL,
	"aggregateid" text NOT NULL,
	"type" text NOT NULL,
	"payload" "bytea" NOT NULL,
	"traceparent" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "message_service"."processed_events" (
	"event_id" text NOT NULL,
	"consumer" text NOT NULL,
	"processed_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "processed_events_event_id_consumer_pk" PRIMARY KEY("event_id","consumer")
);
--> statement-breakpoint
ALTER TABLE "message_service"."conversation_reads" ADD CONSTRAINT "conversation_reads_conversation_id_conversations_id_fk" FOREIGN KEY ("conversation_id") REFERENCES "message_service"."conversations"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "message_service"."messages" ADD CONSTRAINT "messages_conversation_id_conversations_id_fk" FOREIGN KEY ("conversation_id") REFERENCES "message_service"."conversations"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE UNIQUE INDEX "conversations_pair_key" ON "message_service"."conversations" USING btree ("kind","customer_id","counterpart_id");--> statement-breakpoint
CREATE INDEX "conversations_customer_idx" ON "message_service"."conversations" USING btree ("customer_id","last_message_at");--> statement-breakpoint
CREATE INDEX "conversations_counterpart_idx" ON "message_service"."conversations" USING btree ("counterpart_id","last_message_at");--> statement-breakpoint
CREATE UNIQUE INDEX "messages_seq_key" ON "message_service"."messages" USING btree ("conversation_id","seq");--> statement-breakpoint
CREATE UNIQUE INDEX "messages_client_id_key" ON "message_service"."messages" USING btree ("conversation_id","sender_id","client_message_id");