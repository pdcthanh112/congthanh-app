import 'reflect-metadata';
import { bootstrap } from '@ecom/platform';
import { AppModule } from './app.module.js';
import { env } from './config/env.js';

const config = env();
await bootstrap(AppModule.forRoot(config), { port: config.HTTP_PORT, cors: config.NODE_ENV === 'development' });
