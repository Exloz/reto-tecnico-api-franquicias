locals {
  azs = {
    for index, availability_zone in var.availability_zones : index => availability_zone
  }
  subnet_cidrs = {
    public      = cidrsubnet(var.vpc_cidr, 8, 0)
    integration = [for index in range(2) : cidrsubnet(var.vpc_cidr, 8, 10 + index)]
    application = [for index in range(2) : cidrsubnet(var.vpc_cidr, 8, 20 + index)]
    data        = [for index in range(2) : cidrsubnet(var.vpc_cidr, 8, 30 + index)]
  }
  vpc_dns_resolver = "${cidrhost(var.vpc_cidr, 2)}/32"
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-vpc" })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name_prefix}-igw" })
}

resource "aws_subnet" "public_nat" {
  vpc_id                  = aws_vpc.this.id
  availability_zone       = var.availability_zones[0]
  cidr_block              = local.subnet_cidrs.public
  map_public_ip_on_launch = false

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-public-nat-${var.availability_zones[0]}"
    Tier = "public-nat"
  })
}

resource "aws_subnet" "integration" {
  for_each = local.azs

  vpc_id                  = aws_vpc.this.id
  availability_zone       = each.value
  cidr_block              = local.subnet_cidrs.integration[each.key]
  map_public_ip_on_launch = false

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-private-integration-${each.value}"
    Tier = "private-integration"
  })
}

resource "aws_subnet" "application" {
  for_each = local.azs

  vpc_id                  = aws_vpc.this.id
  availability_zone       = each.value
  cidr_block              = local.subnet_cidrs.application[each.key]
  map_public_ip_on_launch = false

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-private-application-${each.value}"
    Tier = "private-application"
  })
}

resource "aws_subnet" "data" {
  for_each = local.azs

  vpc_id                  = aws_vpc.this.id
  availability_zone       = each.value
  cidr_block              = local.subnet_cidrs.data[each.key]
  map_public_ip_on_launch = false

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-private-data-${each.value}"
    Tier = "private-data"
  })
}

resource "aws_eip" "nat" {
  domain = "vpc"

  depends_on = [aws_internet_gateway.this]

  tags = merge(var.tags, { Name = "${var.name_prefix}-nat-eip" })
}

resource "aws_nat_gateway" "this" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public_nat.id

  depends_on = [aws_internet_gateway.this]

  tags = merge(var.tags, { Name = "${var.name_prefix}-nat" })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name_prefix}-public-routes" })
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public_nat" {
  subnet_id      = aws_subnet.public_nat.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "integration" {
  for_each = local.azs

  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name_prefix}-integration-routes-${each.value}" })
}

resource "aws_route" "integration_nat" {
  for_each = local.azs

  route_table_id         = aws_route_table.integration[each.key].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.this.id
}

resource "aws_route_table_association" "integration" {
  for_each = local.azs

  subnet_id      = aws_subnet.integration[each.key].id
  route_table_id = aws_route_table.integration[each.key].id
}

resource "aws_route_table" "application" {
  for_each = local.azs

  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name_prefix}-application-routes-${each.value}" })
}

resource "aws_route" "application_nat" {
  for_each = local.azs

  route_table_id         = aws_route_table.application[each.key].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.this.id
}

resource "aws_route_table_association" "application" {
  for_each = local.azs

  subnet_id      = aws_subnet.application[each.key].id
  route_table_id = aws_route_table.application[each.key].id
}

resource "aws_route_table" "data" {
  for_each = local.azs

  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name_prefix}-data-routes-${each.value}" })
}

resource "aws_route_table_association" "data" {
  for_each = local.azs

  subnet_id      = aws_subnet.data[each.key].id
  route_table_id = aws_route_table.data[each.key].id
}

data "aws_region" "current" {}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${data.aws_region.current.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids = concat(
    [for route_table in aws_route_table.integration : route_table.id],
    [for route_table in aws_route_table.application : route_table.id]
  )

  tags = merge(var.tags, { Name = "${var.name_prefix}-s3-endpoint" })
}

resource "aws_cloudwatch_log_group" "vpc_flow_logs" {
  name              = "/aws/vpc/${var.name_prefix}/flow-logs"
  retention_in_days = var.flow_log_retention_days

  tags = var.tags
}

data "aws_iam_policy_document" "flow_logs_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["vpc-flow-logs.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "vpc_flow_logs" {
  name                 = "${var.name_prefix}-vpc-flow-logs"
  assume_role_policy   = data.aws_iam_policy_document.flow_logs_assume_role.json
  permissions_boundary = var.permissions_boundary_arn

  tags = var.tags
}

data "aws_iam_policy_document" "flow_logs" {
  statement {
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
    resources = ["${aws_cloudwatch_log_group.vpc_flow_logs.arn}:*"]
  }

  statement {
    actions = [
      "logs:DescribeLogGroups",
      "logs:DescribeLogStreams"
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "vpc_flow_logs" {
  name   = "${var.name_prefix}-vpc-flow-logs"
  role   = aws_iam_role.vpc_flow_logs.id
  policy = data.aws_iam_policy_document.flow_logs.json
}

resource "aws_flow_log" "this" {
  iam_role_arn    = aws_iam_role.vpc_flow_logs.arn
  log_destination = aws_cloudwatch_log_group.vpc_flow_logs.arn
  traffic_type    = var.flow_log_traffic_type
  vpc_id          = aws_vpc.this.id

  tags = var.tags
}

resource "aws_security_group" "vpc_link" {
  name                   = "${var.name_prefix}-vpc-link"
  description            = "API Gateway VPC Link to the internal ALB"
  vpc_id                 = aws_vpc.this.id
  revoke_rules_on_delete = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-vpc-link" })
}

resource "aws_security_group" "alb" {
  name                   = "${var.name_prefix}-alb"
  description            = "Internal ALB traffic between VPC Link and API tasks"
  vpc_id                 = aws_vpc.this.id
  revoke_rules_on_delete = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-alb" })
}

resource "aws_security_group" "api" {
  name                   = "${var.name_prefix}-api"
  description            = "Franchise API tasks"
  vpc_id                 = aws_vpc.this.id
  revoke_rules_on_delete = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-api" })
}

resource "aws_security_group" "flyway" {
  name                   = "${var.name_prefix}-flyway"
  description            = "Flyway migration tasks"
  vpc_id                 = aws_vpc.this.id
  revoke_rules_on_delete = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-flyway" })
}

resource "aws_security_group" "bootstrap" {
  name                   = "${var.name_prefix}-bootstrap"
  description            = "Database bootstrap tasks"
  vpc_id                 = aws_vpc.this.id
  revoke_rules_on_delete = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-bootstrap" })
}

resource "aws_security_group" "rds" {
  name                   = "${var.name_prefix}-rds"
  description            = "PostgreSQL access from approved ECS tasks"
  vpc_id                 = aws_vpc.this.id
  revoke_rules_on_delete = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-rds" })
}

resource "aws_vpc_security_group_egress_rule" "vpc_link_to_alb" {
  security_group_id            = aws_security_group.vpc_link.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = var.alb_listener_port
  to_port                      = var.alb_listener_port
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_vpc_link" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.vpc_link.id
  ip_protocol                  = "tcp"
  from_port                    = var.alb_listener_port
  to_port                      = var.alb_listener_port
}

resource "aws_vpc_security_group_egress_rule" "alb_to_api" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.api.id
  ip_protocol                  = "tcp"
  from_port                    = var.application_port
  to_port                      = var.application_port
}

resource "aws_vpc_security_group_ingress_rule" "api_from_alb" {
  security_group_id            = aws_security_group.api.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = var.application_port
  to_port                      = var.application_port
}

resource "aws_vpc_security_group_egress_rule" "api_to_rds" {
  security_group_id            = aws_security_group.api.id
  referenced_security_group_id = aws_security_group.rds.id
  ip_protocol                  = "tcp"
  from_port                    = var.postgres_port
  to_port                      = var.postgres_port
}

resource "aws_vpc_security_group_egress_rule" "flyway_to_rds" {
  security_group_id            = aws_security_group.flyway.id
  referenced_security_group_id = aws_security_group.rds.id
  ip_protocol                  = "tcp"
  from_port                    = var.postgres_port
  to_port                      = var.postgres_port
}

resource "aws_vpc_security_group_egress_rule" "bootstrap_to_rds" {
  security_group_id            = aws_security_group.bootstrap.id
  referenced_security_group_id = aws_security_group.rds.id
  ip_protocol                  = "tcp"
  from_port                    = var.postgres_port
  to_port                      = var.postgres_port
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_api" {
  security_group_id            = aws_security_group.rds.id
  referenced_security_group_id = aws_security_group.api.id
  ip_protocol                  = "tcp"
  from_port                    = var.postgres_port
  to_port                      = var.postgres_port
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_flyway" {
  security_group_id            = aws_security_group.rds.id
  referenced_security_group_id = aws_security_group.flyway.id
  ip_protocol                  = "tcp"
  from_port                    = var.postgres_port
  to_port                      = var.postgres_port
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_bootstrap" {
  security_group_id            = aws_security_group.rds.id
  referenced_security_group_id = aws_security_group.bootstrap.id
  ip_protocol                  = "tcp"
  from_port                    = var.postgres_port
  to_port                      = var.postgres_port
}

resource "aws_vpc_security_group_egress_rule" "api_https" {
  security_group_id = aws_security_group.api.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "flyway_https" {
  security_group_id = aws_security_group.flyway.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "bootstrap_https" {
  security_group_id = aws_security_group.bootstrap.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "task_dns_udp" {
  for_each = {
    api       = aws_security_group.api.id
    flyway    = aws_security_group.flyway.id
    bootstrap = aws_security_group.bootstrap.id
  }

  security_group_id = each.value
  cidr_ipv4         = local.vpc_dns_resolver
  ip_protocol       = "udp"
  from_port         = 53
  to_port           = 53
}

resource "aws_vpc_security_group_egress_rule" "task_dns_tcp" {
  for_each = {
    api       = aws_security_group.api.id
    flyway    = aws_security_group.flyway.id
    bootstrap = aws_security_group.bootstrap.id
  }

  security_group_id = each.value
  cidr_ipv4         = local.vpc_dns_resolver
  ip_protocol       = "tcp"
  from_port         = 53
  to_port           = 53
}
