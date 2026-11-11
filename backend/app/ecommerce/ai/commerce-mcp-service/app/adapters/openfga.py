"""Authorizer qua OpenFGA HTTP API (POST /stores/{store}/check), fail-closed."""

from __future__ import annotations

import httpx

from ..domain import Tuple, Unavailable


class OpenFgaAuthorizer:
    def __init__(self, client: httpx.AsyncClient, store_id: str, model_id: str = ""):
        if not store_id:
            raise ValueError("OPENFGA_STORE_ID is required")
        self.client, self.store_id, self.model_id = client, store_id, model_id

    async def check(self, user: str, relation: str, obj: str, contextual: list[Tuple]) -> bool:
        body: dict = {"tuple_key": {"user": user, "relation": relation, "object": obj}}
        if contextual:
            body["contextual_tuples"] = {
                "tuple_keys": [{"user": t.user, "relation": t.relation, "object": t.object} for t in contextual]
            }
        if self.model_id:
            body["authorization_model_id"] = self.model_id
        try:
            r = await self.client.post(f"/stores/{self.store_id}/check", json=body)
        except httpx.HTTPError as e:
            raise Unavailable("authorization service is unavailable") from e
        if r.status_code != 200:
            raise Unavailable(f"authorization service error ({r.status_code})")
        return bool(r.json().get("allowed"))
