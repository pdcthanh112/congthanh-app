import { createParamDecorator, type ExecutionContext, SetMetadata } from "@nestjs/common";
import { GqlExecutionContext } from "@nestjs/graphql";
import type { Principal } from "./principal.js";

export const IS_PUBLIC = "platform:isPublic";
export const ROLES = "platform:roles";

export const Public = () => SetMetadata(IS_PUBLIC, true);

export const Roles = (...roles: string[]) => SetMetadata(ROLES, roles);

export interface RequestWithPrincipal {
  headers: Record<string, string | string[] | undefined>;
  principal?: Principal;
}

export function requestOf(ctx: ExecutionContext): RequestWithPrincipal | undefined {
  if (ctx.getType<string>() === "graphql") {
    const gql = GqlExecutionContext.create(ctx).getContext<{ req?: RequestWithPrincipal }>();
    return gql.req;
  }
  if (ctx.getType() === "http") {
    return ctx.switchToHttp().getRequest<RequestWithPrincipal>();
  }
  return undefined;
}

export const CurrentPrincipal = createParamDecorator((_: unknown, ctx: ExecutionContext): Principal | undefined => requestOf(ctx)?.principal);
