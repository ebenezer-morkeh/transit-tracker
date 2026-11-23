# 08: Terraform for AWS + OIDC deploy workflow (written, never applied)

Spec: `.scratch/transit-tracker-v1/spec.md`

**What to build:** Infrastructure as code that would deploy the service to AWS at minimum cost, plus a CD workflow, both validated for free and never applied. Terraform (us-east-1): ECR repository; ECS cluster + Fargate service (0.25 vCPU / 0.5 GB, one task) in public subnets with a public IP, no NAT Gateway, no load balancer; RDS Postgres db.t4g.micro single-AZ, not publicly accessible, only reachable from the task's security group; CloudWatch log group (short retention) + one alarm; GitHub OIDC provider + IAM role limited to pushing to this ECR repo and updating this ECS service. Local state with a commented-out S3 backend block. CD workflow deploys on push to `main` and skips itself when the `AWS_ROLE_ARN` repo variable isn't set. CI gains Terraform checks.

**Blocked by:** 03, 05

**Status:** ready-for-agent

- [ ] Terraform covers every resource listed above, with variables for region, image tag, and DB credentials (DB password via a sensitive variable or a generated secret, never committed)
- [ ] `terraform fmt -check` and `terraform init -backend=false && terraform validate` pass locally
- [ ] CI workflow runs those Terraform checks without AWS credentials
- [ ] CD workflow: OIDC role assumption, build and push image to ECR, update ECS service; guarded so it skips when `AWS_ROLE_ARN` is unset
- [ ] No `terraform plan`/`apply` run, no AWS resources created, no AWS credentials used
