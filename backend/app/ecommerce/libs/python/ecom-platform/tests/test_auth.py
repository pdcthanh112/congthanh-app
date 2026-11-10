import json
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from jwt.algorithms import RSAAlgorithm

from ecom_platform.auth import AuthError, Verifier, bearer, extract_roles

KEY = rsa.generate_private_key(public_exponent=65537, key_size=2048)
JWK = json.loads(RSAAlgorithm.to_jwk(KEY.public_key()))
JWK.update(kid="k1", alg="RS256", use="sig")


@pytest.fixture(scope="module")
def jwks_url():
    class H(BaseHTTPRequestHandler):
        def do_GET(self):
            body = json.dumps({"keys": [JWK]}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *a):
            pass

    srv = HTTPServer(("127.0.0.1", 0), H)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    yield f"http://127.0.0.1:{srv.server_port}/certs"
    srv.shutdown()


def token(**claims):
    base = {"sub": "u1", "iss": "http://kc/realms/ecommerce", "exp": int(time.time()) + 60}
    base.update(claims)
    return jwt.encode(base, KEY, algorithm="RS256", headers={"kid": "k1"})


def test_verify_roles(jwks_url):
    v = Verifier(jwks_url, issuer="http://kc/realms/ecommerce")
    p = v.verify(
        token(realm_access={"roles": ["role_admin", "customer"]}, resource_access={"web": {"roles": ["seller"]}})
    )
    assert p.subject == "u1" and p.has_role("ADMIN") and p.has_role("seller") and not p.has_role("SUPPORT")


def test_rejects_bad_tokens(jwks_url):
    v = Verifier(jwks_url, issuer="http://kc/realms/ecommerce")
    with pytest.raises(AuthError):
        v.verify(token(exp=int(time.time()) - 3600))
    with pytest.raises(AuthError):
        v.verify(token(iss="http://evil"))
    with pytest.raises(AuthError):
        v.verify("not-a-jwt")
    other = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    with pytest.raises(AuthError):
        v.verify(
            jwt.encode({"sub": "x", "exp": int(time.time()) + 60}, other, algorithm="RS256", headers={"kid": "k1"})
        )


def test_helpers():
    assert bearer("Bearer abc") == "abc" and bearer("Basic abc") is None and bearer(None) is None
    assert extract_roles({"roles": ["x"], "realm_access": None}) == {"X"}
