terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Local state by default. To share state, create the bucket first, then uncomment:
  # backend "s3" {
  #   bucket       = "transit-tracker-tfstate"
  #   key          = "transit-tracker/terraform.tfstate"
  #   region       = "us-east-1"
  #   use_lockfile = true
  # }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = { Project = var.name }
  }
}
