variable "region" {
  description = "AWS region."
  type        = string
  default     = "us-east-1"
}

variable "name" {
  description = "Name prefix for all resources. The deploy workflow assumes the default."
  type        = string
  default     = "transit-tracker"
}

variable "github_repo" {
  description = "GitHub repository (owner/name) allowed to deploy via OIDC from its main branch."
  type        = string
}

variable "image_tag" {
  description = "Image tag the first task definition uses. The deploy workflow rolls out newer tags."
  type        = string
  default     = "latest"
}

variable "db_name" {
  description = "Postgres database name."
  type        = string
  default     = "transit"
}

variable "db_username" {
  description = "Postgres master username. RDS generates the password and keeps it in Secrets Manager."
  type        = string
  default     = "transit"
}
