import 'reflect-metadata';
import { emitSchema } from '@ecommerce/platform';
import { AppModule } from './app.module.js';
import { env } from './config/env.js';

// Ghi schema.graphql (SDL subgraph) để compose supergraph; không cần Postgres.
await emitSchema(AppModule.forRoot({ ...env(), KAFKA_BROKERS: [] }));
