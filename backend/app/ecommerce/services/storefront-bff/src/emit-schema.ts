import 'reflect-metadata';
import { emitSchema } from '@ecom/platform';
import { AppModule } from './app.module.js';
import { env } from './config/env.js';

// Ghi schema.graphql (SDL subgraph) để compose supergraph; không gọi service phía sau.
await emitSchema(AppModule.forRoot(env()));
