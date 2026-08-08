output "api_id" {
  value = aws_apigatewayv2_api.this.id
}

output "api_endpoint" {
  value = aws_apigatewayv2_api.this.api_endpoint
}

output "default_stage_id" {
  value = aws_apigatewayv2_stage.default.id
}

output "vpc_link_id" {
  value = aws_apigatewayv2_vpc_link.this.id
}

output "integration_id" {
  value = aws_apigatewayv2_integration.alb.id
}

output "access_log_group_name" {
  value = aws_cloudwatch_log_group.this.name
}
