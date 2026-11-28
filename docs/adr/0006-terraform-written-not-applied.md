# 0006: Terraform written and validated, never applied

Status: superseded (2026-11-28): Terraform and the deploy workflow removed

Originally (2026-11-22): `infra/` described ECR, ECS Fargate, RDS Postgres, CloudWatch and a GitHub OIDC role, and `.github/workflows/deploy.yml` deployed to it. Neither was ever run against AWS; CI only ran `terraform fmt -check` and `validate`, and the deploy job skipped unless `AWS_ROLE_ARN` was set. Applying it would have cost roughly $15–25/month.

Why removed: the project runs locally only (`docker compose up`) and must cost $0. Code that was never applied was unverified, predated the rider web app (no HTTPS, no load balancer) and added a CI job for nothing. Public hosting is a separate, later decision; it starts from scratch then, and git history has the old Terraform (commit 4a32aac).
