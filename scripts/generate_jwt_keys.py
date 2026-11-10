#!/usr/bin/env python3
"""
ShareRide - Ed25519 (EdDSA) JWT Key Generation Tool
---------------------------------------------------
Generates asymmetric cryptographic key pairs for Edwards-curve Digital Signature (EdDSA / Ed25519)
compliant with RFC 7517 (JWK), RFC 7519 (JWT), RFC 8037 (CFRG ECDH & EdDSA), and RFC 8410 (PKCS#8).

Outputs:
  - Private Key JWK (used by Auth Service for signing)
  - Public Key JWK (used by API Gateway and downstream Resource Servers for verification)
  - Standard PEM files (PKCS#8 private, SubjectPublicKeyInfo public)
  - Ready-to-use configuration for application.yml and environment variables (.env)
"""

import argparse
import base64
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path


def generate_eddsa_keys_openssl():
    """Generate Ed25519 key pair using OpenSSL."""
    openssl_path = shutil.which("openssl")
    if not openssl_path:
        raise RuntimeError("OpenSSL executable not found in PATH.")

    # 1. Generate Ed25519 private key in PKCS#8 PEM format
    priv_proc = subprocess.run(
        [openssl_path, "genpkey", "-algorithm", "ED25519"],
        capture_output=True,
        text=True,
        check=True,
    )
    priv_pem = priv_proc.stdout

    # 2. Derive Ed25519 public key in SubjectPublicKeyInfo PEM format
    pub_proc = subprocess.run(
        [openssl_path, "pkey", "-pubout"],
        input=priv_pem,
        capture_output=True,
        text=True,
        check=True,
    )
    pub_pem = pub_proc.stdout

    # 3. Extract raw 32-byte key material from ASN.1 DER structures (RFC 8410)
    priv_lines = [line.strip() for line in priv_pem.splitlines() if not line.startswith("-----")]
    priv_der = base64.b64decode("".join(priv_lines))
    # RFC 8410: Last 32 bytes of 48-byte PKCS#8 DER is the raw private key seed (curve parameter d)
    raw_priv = priv_der[-32:]

    pub_lines = [line.strip() for line in pub_pem.splitlines() if not line.startswith("-----")]
    pub_der = base64.b64decode("".join(pub_lines))
    # RFC 8410: Last 32 bytes of 44-byte SubjectPublicKeyInfo DER is the raw public key (curve parameter x)
    raw_pub = pub_der[-32:]

    return raw_priv, raw_pub, priv_pem, pub_pem


def build_jwk_pair(raw_priv, raw_pub, key_id="shareride-auth-key-1"):
    """Build RFC 8037 / RFC 7517 compliant OctetKeyPair JWK dictionaries."""
    d_b64url = base64.urlsafe_b64encode(raw_priv).decode("utf-8").rstrip("=")
    x_b64url = base64.urlsafe_b64encode(raw_pub).decode("utf-8").rstrip("=")

    private_jwk = {
        "kty": "OKP",
        "crv": "Ed25519",
        "kid": key_id,
        "use": "sig",
        "d": d_b64url,
        "x": x_b64url,
    }

    public_jwk = {
        "kty": "OKP",
        "crv": "Ed25519",
        "kid": key_id,
        "use": "sig",
        "x": x_b64url,
    }

    return private_jwk, public_jwk


def main():
    parser = argparse.ArgumentParser(
        description="Generate EdDSA (Ed25519) cryptographic keys for ShareRide Auth & Gateway"
    )
    parser.add_argument(
        "--kid",
        default="shareride-auth-key-1",
        help="Key ID (kid) for the JWK header (default: shareride-auth-key-1)",
    )
    parser.add_argument(
        "--out-dir",
        default="config/jwt",
        help="Output directory to save key files (default: config/jwt)",
    )
    parser.add_argument(
        "--env-file",
        help="Optional path to .env file to update/append with JWT keys",
    )
    parser.add_argument(
        "--print-json",
        action="store_true",
        help="Print JSON JWKs to stdout",
    )
    args = parser.parse_args()

    try:
        raw_priv, raw_pub, priv_pem, pub_pem = generate_eddsa_keys_openssl()
    except Exception as e:
        print(f"Error generating keys: {e}", file=sys.stderr)
        sys.exit(1)

    private_jwk, public_jwk = build_jwk_pair(raw_priv, raw_pub, args.kid)
    priv_jwk_json = json.dumps(private_jwk)
    pub_jwk_json = json.dumps(public_jwk)

    # Ensure output directory exists
    out_path = Path(args.out_dir)
    out_path.mkdir(parents=True, exist_ok=True)

    # Write files
    (out_path / "private_key.jwk.json").write_text(json.dumps(private_jwk, indent=2) + "\n")
    (out_path / "public_key.jwk.json").write_text(json.dumps(public_jwk, indent=2) + "\n")
    (out_path / "private_key.pem").write_text(priv_pem)
    (out_path / "public_key.pem").write_text(pub_pem)

    # Set strict permissions on private key files (chmod 600)
    try:
        os.chmod(out_path / "private_key.jwk.json", 0o600)
        os.chmod(out_path / "private_key.pem", 0o600)
    except Exception:
        pass

    if args.env_file:
        env_path = Path(args.env_file)
        lines = []
        if env_path.exists():
            lines = [
                line
                for line in env_path.read_text().splitlines()
                if not any(
                    line.startswith(k)
                    for k in ["JWT_KEY_ID=", "JWT_PRIVATE_KEY_JWK=", "JWT_PUBLIC_KEY_JWK="]
                )
            ]
        lines.append(f"JWT_KEY_ID={args.kid}")
        lines.append(f"JWT_PRIVATE_KEY_JWK='{priv_jwk_json}'")
        lines.append(f"JWT_PUBLIC_KEY_JWK='{pub_jwk_json}'")
        env_path.write_text("\n".join(lines) + "\n")
        print(f"Updated environment file: {env_path.resolve()}")

    if args.print_json:
        print(json.dumps({"private_jwk": private_jwk, "public_jwk": public_jwk}, indent=2))
    else:
        print("================================================================================")
        print(" ShareRide Ed25519 (EdDSA) Asymmetric Key Pair Generated Successfully")
        print("================================================================================")
        print(f"Key ID (kid) : {args.kid}")
        print(f"Algorithm    : EdDSA (Curve Ed25519)")
        print(f"Saved Files  :")
        print(f"  - Auth Private JWK   : {out_path / 'private_key.jwk.json'}")
        print(f"  - Gateway Public JWK : {out_path / 'public_key.jwk.json'}")
        print(f"  - PKCS#8 Private PEM : {out_path / 'private_key.pem'}")
        print(f"  - X.509 Public PEM   : {out_path / 'public_key.pem'}")
        print("--------------------------------------------------------------------------------")
        print("[Auth Service Configuration (Signing Key)]")
        print(f"JWT_PRIVATE_KEY_JWK='{priv_jwk_json}'")
        print("--------------------------------------------------------------------------------")
        print("[Gateway Service Configuration (Verification Key)]")
        print(f"JWT_PUBLIC_KEY_JWK='{pub_jwk_json}'")
        print("================================================================================")


if __name__ == "__main__":
    main()
