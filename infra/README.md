# Infrastructure (written, never applied)

Terraform for running the service on AWS (us-east-1) at minimum cost. **It has never been applied**: CI only runs `terraform fmt -check` and `terraform validate`, which need no AWS account, so the project costs $0.

## What it creates

- VPC with two public subnets (RDS needs two AZs), internet gateway, no NAT Gateway, no load balancer
- ECR repository (keeps the last 10 images)
- ECS cluster and one Fargate task (0.25 vCPU / 0.5 GB) with a public IP, API on port 8080
- RDS Postgres 17, db.t4g.micro, single-AZ, not publicly accessible, port 5432 open only to the task's security group. RDS generates the password and stores it in Secrets Manager; ECS injects it as `SPRING_DATASOURCE_PASSWORD`
- CloudWatch log group (7-day retention) and a CPU > 80% alarm
- GitHub OIDC provider and a deploy role that only the repo's `main` branch can assume, allowed to push to this ECR repo and update this ECS service

## Rough monthly cost if applied

About **$15–25/month**: Fargate 0.25 vCPU / 0.5 GB (~$9), db.t4g.micro plus 20 GB gp3 (~$14, less under the free tier), public IPv4 address (~$3.60), plus small CloudWatch, ECR and Secrets Manager charges.

## Apply and tear down

```sh
cd infra
terraform init
terraform apply -var github_repo=OWNER/transit-tracker
# push an image to the ECR repo, then set the repo variable AWS_ROLE_ARN to the github_deploy_role_arn output
terraform destroy -var github_repo=OWNER/transit-tracker
```

State is local. To use S3, uncomment the backend block in `versions.tf`.

## Deploy workflow variables

`.github/workflows/deploy.yml` reads these GitHub repository variables (Settings → Secrets and variables → Actions → Variables). Only `AWS_ROLE_ARN` is required; the rest fall back to the defaults below, which match this Terraform with `var.name = "transit-tracker"` and `var.region = "us-east-1"`. Set them if you change those.

| Variable | Default | Terraform source |
|---|---|---|
| `AWS_ROLE_ARN` | none (workflow skips until set) | `github_deploy_role_arn` output |
| `AWS_REGION` | `us-east-1` | `var.region` |
| `ECR_REPOSITORY` | `transit-tracker` | ECR repository name (`var.name`) |
| `ECS_CLUSTER` | `transit-tracker` | ECS cluster name (`var.name`) |
| `ECS_SERVICE` | `transit-tracker` | ECS service name (`var.name`) |
| `TASK_FAMILY` | `transit-tracker` | task definition family (`var.name`) |
| `CONTAINER_NAME` | `transit-tracker` | container name in the task definition (`var.name`) |
