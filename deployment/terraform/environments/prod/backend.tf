terraform {
  backend "s3" {
    bucket       = "franchise-127321794531-terraform-state"
    key          = "prod/infrastructure.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}
