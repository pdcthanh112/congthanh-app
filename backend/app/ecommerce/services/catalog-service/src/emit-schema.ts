import { emitSchema } from "@ecommerce/platform";
import "reflect-metadata";
import { AppModule } from "./app.module.js";
import { env } from "./config/env.js";

await emitSchema(AppModule.forRoot({ ...env(), GRPC_PORT: 0 } as ReturnType<typeof env>));
