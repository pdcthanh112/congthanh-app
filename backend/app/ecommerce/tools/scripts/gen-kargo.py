from __future__ import annotations

import pathlib
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "platform/delivery/kargo/pipeline.yaml"
REPO = "https://github.com/pdcthanh112/CongThanhApp.git"
APP = "backend/app/ecommerce"
PROJECT = "congthanhapp"
STREAM_IMAGE = "ghcr.io/pdcthanh112/congthanhapp/stream-processor"
STAGES = ["dev", "staging", "prod"]


def services() -> list[tuple[str, str]]:
    out = []
    for d in sorted((ROOT / "deploy/helm/values").iterdir()):
        v = d / "values.yaml"
        if d.is_dir() and v.exists():
            repo = (yaml.safe_load(v.read_text()) or {}).get("image", {}).get("repository")
            if repo:
                out.append((d.name, repo))
    return out


def build() -> str:
    svcs = services()
    docs: list[dict] = [
        {
            "apiVersion": "kargo.akuity.io/v1alpha1",
            "kind": "Project",
            "metadata": {"name": PROJECT, "annotations": {"argocd.argoproj.io/sync-wave": "-1"}},
        },
        {
            "apiVersion": "kargo.akuity.io/v1alpha1",
            "kind": "ProjectConfig",
            "metadata": {"name": PROJECT, "namespace": PROJECT},
            "spec": {
                "promotionPolicies": [
                    {"stageSelector": {"name": "dev"}, "autoPromotionEnabled": True},
                    {"stageSelector": {"name": "staging"}, "autoPromotionEnabled": True},
                    {"stageSelector": {"name": "prod"}, "autoPromotionEnabled": False},
                ]
            },
        },
        {
            "apiVersion": "kargo.akuity.io/v1alpha1",
            "kind": "Warehouse",
            "metadata": {"name": "services", "namespace": PROJECT},
            "spec": {
                "interval": "5m",
                "freightCreationPolicy": "Automatic",
                "subscriptions": [
                    {
                        "name": name,
                        "image": {
                            "repoURL": repo,
                            "imageSelectionStrategy": "NewestBuild",
                            "allowTagsRegexes": ["^[0-9a-f]{12}$"],
                            "discoveryLimit": 5,
                        },
                    }
                    for name, repo in [*svcs, ("stream-processor", STREAM_IMAGE)]
                ],
            },
        },
    ]

    steps: list[dict] = [
        {
            "uses": "git-clone",
            "config": {
                "repoURL": REPO,
                "checkout": [{"branch": "main", "path": "./src"}],
                "author": {"name": "kargo", "email": "kargo@congthanhapp.local"},
            },
        }
    ]
    for name, repo in svcs:
        steps.append(
            {
                "uses": "yaml-update",
                "as": f"update-{name}",
                "config": {
                    "path": f"./src/{APP}/deploy/helm/values/{name}/values-${{{{ vars.env }}}}.yaml",
                    "updates": [
                        {"key": "image.tag", "value": f"${{{{ imageFrom('{repo}').Tag }}}}"},
                        {"key": "image.digest", "value": f"${{{{ imageFrom('{repo}').Digest }}}}"},
                    ],
                },
            }
        )
    steps += [
        {
            "uses": "kustomize-set-image",
            "as": "update-stream-processor",
            "config": {
                "path": f"./src/{APP}/platform/streaming/flink/overlays/${{{{ vars.env }}}}",
                "images": [{"image": STREAM_IMAGE, "digest": f"${{{{ imageFrom('{STREAM_IMAGE}').Digest }}}}"}],
            },
        },
        {
            "uses": "git-commit",
            "as": "commit",
            "config": {
                "path": "./src",
                "message": "kargo: promote ${{ ctx.stage }} lên freight ${{ ctx.targetFreight.name }}",
            },
        },
    ]
    docs.append(
        {
            "apiVersion": "kargo.akuity.io/v1alpha1",
            "kind": "PromotionTask",
            "metadata": {"name": "update-images", "namespace": PROJECT},
            "spec": {"vars": [{"name": "env"}], "steps": steps},
        }
    )

    for i, env in enumerate(STAGES):
        source = {"direct": True} if i == 0 else {"stages": [STAGES[i - 1]]}
        tail: list[dict]
        if env == "prod":
            tail = [
                {"uses": "git-push", "as": "push", "config": {"path": "./src", "generateTargetBranch": True}},
                {
                    "uses": "git-open-pr",
                    "as": "open-pr",
                    "config": {
                        "repoURL": REPO,
                        "sourceBranch": "${{ outputs.push.branch }}",
                        "targetBranch": "main",
                        "title": "release(prod): freight ${{ ctx.targetFreight.name }}",
                        "labels": ["release", "kargo"],
                    },
                },
                {
                    "uses": "git-wait-for-pr",
                    "config": {"repoURL": REPO, "prNumber": "${{ outputs['open-pr'].pr.id }}"},
                },
            ]
        else:
            tail = [{"uses": "git-push", "config": {"path": "./src", "targetBranch": "main"}}]
        docs.append(
            {
                "apiVersion": "kargo.akuity.io/v1alpha1",
                "kind": "Stage",
                "metadata": {"name": env, "namespace": PROJECT, "labels": {"environment": env}},
                "spec": {
                    "requestedFreight": [{"origin": {"kind": "Warehouse", "name": "services"}, "sources": source}],
                    "promotionTemplate": {
                        "spec": {
                            "vars": [{"name": "env", "value": env}],
                            "steps": [{"task": {"name": "update-images"}}, *tail],
                        }
                    },
                    "verification": {"analysisTemplates": [{"name": "ecommerce-health"}]},
                },
            }
        )
    head = "# SINH bằng tools/scripts/gen-kargo.py từ deploy/helm/values/* — đừng sửa tay.\n"
    return head + yaml.safe_dump_all(docs, sort_keys=False, allow_unicode=True, width=120)


def main() -> None:
    out = build()
    if "--check" in sys.argv:
        if not OUT.exists() or yaml.safe_load_all(OUT.read_text()) and list(yaml.safe_load_all(OUT.read_text())) != list(
            yaml.safe_load_all(out)
        ):
            print("stale:", OUT)
            sys.exit(1)
        return
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(out)
    print("wrote", OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
