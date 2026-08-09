# Despliegue y plataforma AWS

Esta carpeta contiene artefactos operativos y de despliegue. No forma parte de la capa `infrastructure/` de Clean Architecture; esa carpeta queda reservada para entry points, driven adapters y helpers del Scaffold Bancolombia.

```text
deployment/
├── Dockerfile
├── README.md
├── RUNBOOK.md
├── scripts/
└── terraform/
    ├── bootstrap/
    ├── environments/dev/
    ├── environments/prod/
    └── modules/
```

Los módulos reutilizables viven en `terraform/modules/`. DEV y PROD son root modules independientes y no usan workspaces. El bootstrap administra el bucket de estado, el rol humano y los permissions boundaries.

La reubicación desde `infrastructure/` no cambia las claves remotas `bootstrap/infrastructure.tfstate`, `dev/infrastructure.tfstate` y `prod/infrastructure.tfstate`; no existe migración de estado. Después de actualizar una copia local, reinicializa cada root para regenerar metadatos y rutas de módulos:

```sh
terraform -chdir=deployment/terraform/bootstrap init -reconfigure
terraform -chdir=deployment/terraform/environments/dev init -reconfigure
terraform -chdir=deployment/terraform/environments/prod init -reconfigure
```

## Requisitos

- Terraform 1.10 o superior.
- Cuenta AWS `127321794531` autenticada sin access keys estáticas.
- Región `us-east-1`.
- Bucket `franchise-127321794531-terraform-state` creado por `terraform/bootstrap/`.
- Rol `arn:aws:iam::127321794531:role/franchise-terraform-apply` asumido por el operador.

Los roots no encadenan `sts:AssumeRole`: backend y provider usan la misma identidad ya asumida. GitHub Actions usa roles OIDC limitados por ambiente y función.

## Validación local

La validación sintáctica no requiere acceso al backend remoto:

```sh
terraform fmt -check -recursive deployment/terraform
terraform -chdir=deployment/terraform/bootstrap init -backend=false
terraform -chdir=deployment/terraform/bootstrap validate
terraform -chdir=deployment/terraform/environments/dev init -backend=false
terraform -chdir=deployment/terraform/environments/dev validate
terraform -chdir=deployment/terraform/environments/prod init -backend=false
terraform -chdir=deployment/terraform/environments/prod validate
```

CI añade TFLint y Trivy. `.trivyignore` registra las excepciones aceptadas para cifrado SSE-S3 del estado, salida HTTPS mediante NAT y terminación TLS en API Gateway.

## Ambientes

DEV mantiene una tarea API para limitar costo. PROD usa una a tres tareas en la configuración aceptada actual. Cada tarea abre como máximo diez conexiones R2DBC; el presupuesto máximo de la API queda por debajo de la alarma de 60 conexiones RDS y conserva margen para migraciones y operación.

Los valores permanentes viven en `terraform/environments/{dev,prod}/*.tfvars`; CI inyecta digests inmutables y habilita transitoriamente la etapa de bootstrap, migración o API que corresponda. Antes de aplicar manualmente, revisa el estado desplegado y evita reemplazar un digest vigente por un valor antiguo del repositorio.

Plan DEV:

```sh
terraform -chdir=deployment/terraform/environments/dev init
terraform -chdir=deployment/terraform/environments/dev plan -var-file=dev.tfvars
```

PROD exige confirmación explícita, cuenta y región esperadas, protección de borrado, snapshot final, backups de 30 días y Multi-AZ. `prod.tfvars` conserva el servicio deshabilitado para permitir crear la foundation sin imágenes; el plan CI detecta el digest desplegado y habilita el servicio cuando ya existe una release. La promoción aporta los nuevos digests validados en DEV.

## Entrega

Los pull requests ejecutan arquitectura, tests, JaCoCo, Terraform, TFLint, Trivy y validación ARM64 cuando corresponde. Mutation testing permanece deshabilitado por decisión explícita y no forma parte del gate CI.

Un push aprobado a `development` construye imágenes inmutables, ejecuta Flyway, despliega DEV y registra tags `deployed-<sha>` después del smoke. Un merge aprobado de `development` a `main` copia los mismos manifiestos a PROD, ejecuta migraciones y despliega con aprobación del GitHub Environment `prod`.

Los scripts en `scripts/` son usados tanto por CI como por operadores. Cada llamada HTTP de smoke tiene timeout total; las tareas ECS esperan exit code `0`. Flyway es forward-only y debe ser compatible con la revisión anterior durante rollback de aplicación.

## Operación

[`RUNBOOK.md`](RUNBOOK.md) contiene autenticación, inspección de ECS/RDS/API Gateway, CloudWatch, k6, consultas read-only, pausa, recuperación y destrucción controlada.

No cambies las claves de backend, no uses imágenes `latest`, no abras RDS a Internet y no recuperes secretos en la terminal. Los secretos de aplicación son inyectados por ECS desde Secrets Manager; la aplicación no crea clientes AWS para leerlos.
