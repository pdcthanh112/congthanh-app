import { join } from 'node:path';
import { ApolloServerPluginLandingPageDisabled } from '@apollo/server/plugin/disabled';
import { ApolloFederationDriver, type ApolloFederationDriverConfig } from '@nestjs/apollo';
import { Logger } from '@nestjs/common';
import { GraphQLError, type GraphQLFormattedError } from 'graphql';
import { DomainError, GRAPHQL_CODE } from '../errors/domain-error.js';
import { graphqlContext } from './subscriptions.js';

export interface SubgraphOptions {
  /** Thư mục service; SDL ghi ra <dir>/schema.graphql để compose supergraph trong CI. */
  serviceDir: string;
  production: boolean;
  extra?: Partial<ApolloFederationDriverConfig>;
}

/**
 * Subgraph Apollo Federation v2 (code-first). Router (Cosmo) ghép các subgraph thành supergraph,
 * chuyển tiếp header Authorization; subgraph vẫn tự kiểm tra token qua AuthGuard.
 */
export function subgraphConfig(opts: SubgraphOptions): ApolloFederationDriverConfig {
  return {
    driver: ApolloFederationDriver,
    // Dev/CI ghi SDL ra file (để compose supergraph); production giữ trong bộ nhớ (filesystem chỉ đọc).
    autoSchemaFile: opts.production
      ? { federation: 2 }
      : { federation: 2, path: join(opts.serviceDir, 'schema.graphql') },
    sortSchema: true,
    path: '/graphql',
    playground: false,
    // Router cần introspection (_service { sdl }) để kiểm tra; client đi qua router đã tắt introspection.
    introspection: true,
    plugins: opts.production ? [ApolloServerPluginLandingPageDisabled()] : [],
    context: graphqlContext,
    formatError: formatGraphQLError,
    ...opts.extra,
  };
}

const log = new Logger('GraphQL');

function isGraphQLErrorLike(e: unknown): e is { originalError: unknown } {
  return typeof e === 'object' && e !== null && (e as Error).name === 'GraphQLError' && 'originalError' in e;
}

export function formatGraphQLError(formatted: GraphQLFormattedError, error: unknown): GraphQLFormattedError {
  // Không dùng instanceof GraphQLError: khi graphql bị nạp hai lần (CJS + ESM, ví dụ trong test)
  // lớp GraphQLError khác nhau và lỗi nghiệp vụ sẽ bị che thành INTERNAL_SERVER_ERROR.
  const original = error instanceof GraphQLError || isGraphQLErrorLike(error) ? error.originalError : error;
  if (original instanceof DomainError) {
    return {
      message: original.message,
      ...(formatted.path ? { path: formatted.path } : {}),
      extensions: { code: GRAPHQL_CODE[original.kind], reason: original.code },
    };
  }
  const code = formatted.extensions?.code;
  if (code === 'GRAPHQL_VALIDATION_FAILED' || code === 'BAD_USER_INPUT' || code === 'GRAPHQL_PARSE_FAILED') {
    return formatted;
  }
  log.error(original instanceof Error ? (original.stack ?? original.message) : formatted.message);
  return {
    message: 'Internal server error',
    ...(formatted.path ? { path: formatted.path } : {}),
    extensions: { code: 'INTERNAL_SERVER_ERROR' },
  };
}
