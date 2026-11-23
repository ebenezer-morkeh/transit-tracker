# 0006: Terraform written and validated, never applied

Status: accepted (2026-11-22)

`infra/` describes ECR, ECS Fargate, RDS Postgres, CloudWatch and a GitHub OIDC role; `.github/workflows/deploy.yml` deploys to it. Neither has been run against AWS. CI only runs `terraform fmt -check` and `validate` (no credentials); the deploy job skips unless the `AWS_ROLE_ARN` repo variable is set.

Why: the project must cost $0. Applying it costs roughly $15–25/month (estimate in `infra/README.md`). Consequence: IAM scoping and the deploy path are unverified until someone applies it. The layout is cost-minimal on purpose (public subnets, no NAT gateway, no load balancer, single-AZ db.t4g.micro); production hardening is listed in the README.
