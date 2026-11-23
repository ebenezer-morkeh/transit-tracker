output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "github_deploy_role_arn" {
  description = "Set this as the AWS_ROLE_ARN repository variable to enable the deploy workflow."
  value       = aws_iam_role.github_deploy.arn
}

output "db_endpoint" {
  value = aws_db_instance.main.address
}

output "db_password_secret_arn" {
  description = "Secrets Manager secret holding the generated DB password."
  value       = aws_db_instance.main.master_user_secret[0].secret_arn
}
