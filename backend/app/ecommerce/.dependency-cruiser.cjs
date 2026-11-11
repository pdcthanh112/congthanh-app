/**
 * Luật kiến trúc lục giác cho service NestJS (tương đương ArchUnit bên Java, go-arch-lint bên Go).
 *   npm run lint  → depcruise services libs/ts --config .dependency-cruiser.cjs
 */
module.exports = {
  forbidden: [
    {
      name: 'domain-is-pure',
      comment: 'domain/ chỉ được dùng domain/ và @ecommerce/platform/errors, không framework, không DB.',
      severity: 'error',
      from: { path: '^services/[^/]+/src/domain/' },
      to: {
        pathNot: ['^services/[^/]+/src/domain/', '^libs/ts/platform/src/errors/', '^libs/ts/shared/'],
        dependencyTypesNot: ['type-only'],
      },
    },
    {
      name: 'application-no-adapters',
      comment: 'application/ không phụ thuộc adapters/ (chỉ qua port).',
      severity: 'error',
      from: { path: '^services/[^/]+/src/application/' },
      to: { path: '^services/[^/]+/src/adapters/' },
    },
    {
      name: 'application-no-infra-libs',
      comment: 'application/ không gọi thẳng drizzle, pg, kafka, connect, graphql.',
      severity: 'error',
      from: { path: '^services/[^/]+/src/application/' },
      to: { path: 'node_modules/(drizzle-orm|pg|@confluentinc|@connectrpc|graphql|@nestjs/graphql|ioredis)/' },
    },
    {
      name: 'inbound-not-outbound',
      comment: 'adapter vào (graphql, grpc, kafka consumer) không dùng thẳng adapter ra.',
      severity: 'error',
      from: { path: '^services/[^/]+/src/adapters/in/' },
      to: { path: '^services/[^/]+/src/adapters/out/' },
    },
    {
      name: 'no-cross-service',
      comment: 'Service không import code service khác; giao tiếp qua proto/GraphQL/event.',
      severity: 'error',
      from: { path: '^services/([^/]+)/' },
      to: { path: '^services/', pathNot: '^services/$1/' },
    },
    { name: 'no-circular', severity: 'error', from: {}, to: { circular: true } },
    {
      name: 'not-to-test',
      severity: 'error',
      from: { pathNot: '\\.test\\.ts$|/test/' },
      to: { path: '\\.test\\.ts$|/test/' },
    },
  ],
  options: {
    doNotFollow: { path: 'node_modules' },
    exclude: { path: '(^|/)(dist|node_modules|src/gen)/' },
    tsPreCompilationDeps: true,
    tsConfig: { fileName: 'tsconfig.base.json' },
    enhancedResolveOptions: { exportsFields: ['exports'], conditionNames: ['source', 'import', 'default'] },
  },
};
