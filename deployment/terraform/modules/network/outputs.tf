output "vpc_id" {
  description = "ID of the VPC."
  value       = aws_vpc.this.id
}

output "vpc_cidr" {
  description = "CIDR assigned to the VPC."
  value       = aws_vpc.this.cidr_block
}

output "public_nat_subnet_id" {
  description = "ID of the public subnet containing the NAT Gateway."
  value       = aws_subnet.public_nat.id
}

output "integration_subnet_ids" {
  description = "Private integration subnet IDs in availability zone input order."
  value       = [for index in range(2) : aws_subnet.integration[index].id]
}

output "application_subnet_ids" {
  description = "Private application subnet IDs in availability zone input order."
  value       = [for index in range(2) : aws_subnet.application[index].id]
}

output "data_subnet_ids" {
  description = "Private data subnet IDs in availability zone input order."
  value       = [for index in range(2) : aws_subnet.data[index].id]
}

output "subnet_cidrs" {
  description = "CIDRs allocated to each network tier."
  value       = local.subnet_cidrs
}

output "nat_gateway_id" {
  description = "ID of the shared NAT Gateway."
  value       = aws_nat_gateway.this.id
}

output "s3_vpc_endpoint_id" {
  description = "ID of the S3 Gateway VPC endpoint."
  value       = aws_vpc_endpoint.s3.id
}

output "flow_log_group_name" {
  description = "CloudWatch log group receiving VPC flow logs."
  value       = aws_cloudwatch_log_group.vpc_flow_logs.name
}

output "security_group_ids" {
  description = "Security group IDs consumed by downstream modules."
  value = {
    vpc_link  = aws_security_group.vpc_link.id
    alb       = aws_security_group.alb.id
    api       = aws_security_group.api.id
    flyway    = aws_security_group.flyway.id
    bootstrap = aws_security_group.bootstrap.id
    rds       = aws_security_group.rds.id
  }
}
