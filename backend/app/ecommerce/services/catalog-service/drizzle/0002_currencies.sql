CREATE TABLE "catalog_service"."currencies" (
	"code" char(3) PRIMARY KEY NOT NULL,
	"numeric_code" char(3) NOT NULL,
	"name" varchar(80) NOT NULL,
	"native_name" varchar(80),
	"symbol" varchar(10) NOT NULL,
	"native_symbol" varchar(10),
	"decimal_digits" smallint NOT NULL,
	"countries" char(2)[] DEFAULT '{}'::char(2)[] NOT NULL,
	"active" boolean DEFAULT true NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "currencies_code_check" CHECK (code ~ '^[A-Z]{3}$'),
	CONSTRAINT "currencies_numeric_code_check" CHECK (numeric_code ~ '^[0-9]{3}$'),
	CONSTRAINT "currencies_decimal_digits_check" CHECK (decimal_digits BETWEEN 0 AND 4)
);

CREATE UNIQUE INDEX "currencies_numeric_code_key" ON "catalog_service"."currencies" USING btree ("numeric_code");--> statement-breakpoint
CREATE INDEX "currencies_countries_idx" ON "catalog_service"."currencies" USING gin ("countries");