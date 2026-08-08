# Proyecto Base Implementando Clean Architecture

## Antes de Iniciar

Empezaremos por explicar los diferentes componentes del proyectos y partiremos de los componentes externos, continuando con los componentes core de negocio (dominio) y por último el inicio y configuración de la aplicación.

Lee el artículo [Clean Architecture — Aislando los detalles](https://medium.com/bancolombia-tech/clean-architecture-aislando-los-detalles-4f9530f35d7a)

# Arquitectura

![Clean Architecture](https://miro.medium.com/max/1400/1*ZdlHz8B0-qu9Y-QO3AXR_w.png)

## Domain

Es el módulo más interno de la arquitectura, pertenece a la capa del dominio y encapsula la lógica y reglas del negocio mediante modelos y entidades del dominio.

## Usecases

Este módulo gradle perteneciente a la capa del dominio, implementa los casos de uso del sistema, define lógica de aplicación y reacciona a las invocaciones desde el módulo de entry points, orquestando los flujos hacia el módulo de entities.

## Infrastructure

### Helpers

En el apartado de helpers tendremos utilidades generales para los Driven Adapters y Entry Points.

Estas utilidades no están arraigadas a objetos concretos, se realiza el uso de generics para modelar comportamientos
genéricos de los diferentes objetos de persistencia que puedan existir, este tipo de implementaciones se realizan
basadas en el patrón de diseño [Unit of Work y Repository](https://medium.com/@krzychukosobudzki/repository-design-pattern-bc490b256006)

Estas clases no puede existir solas y debe heredarse su compartimiento en los **Driven Adapters**

### Driven Adapters

Los driven adapter representan implementaciones externas a nuestro sistema, como lo son conexiones a servicios rest,
soap, bases de datos, lectura de archivos planos, y en concreto cualquier origen y fuente de datos con la que debamos
interactuar.

### Entry Points

Los entry points representan los puntos de entrada de la aplicación o el inicio de los flujos de negocio.

## Application

Este módulo es el más externo de la arquitectura, es el encargado de ensamblar los distintos módulos, resolver las dependencias y crear los beans de los casos de use (UseCases) de forma automática, inyectando en éstos instancias concretas de las dependencias declaradas. Además inicia la aplicación (es el único módulo del proyecto donde encontraremos la función “public static void main(String[] args)”.

**Los beans de los casos de uso se disponibilizan automaticamente gracias a un '@ComponentScan' ubicado en esta capa.**

## Calidad

- Suite completa con JaCoCo y PIT por módulo: `./gradlew test`.
- Arquitectura, cobertura y reporte JaCoCo: `./gradlew qualityGate jacocoMergedReport`.
- Mutation testing y reporte PIT agregado: `./gradlew pitestReportAggregate`.
- Carga local, con la API disponible: `BASE_URL=http://localhost:8080 k6 run load-tests/franchise-api.js`.

Las pruebas de integración usan PostgreSQL 17.6 mediante Testcontainers y requieren Docker. Los reportes agregados quedan en `build/reports/jacocoMergedReport/` y `build/reports/pitest/`.

## Desarrollo Local

- Levantar PostgreSQL, ejecutar migraciones y arrancar la API: `docker-compose up --build -d api`.
- Consultar el estado: `docker-compose ps`.
- Seguir los logs: `docker-compose logs -f api`.
- Ejecutar un smoke de carga: `K6_VUS=1 K6_DURATION=10s docker-compose run --rm k6`.
- Ejecutar la carga completa: `docker-compose run --rm k6`.
- Detener los servicios conservando datos: `docker-compose down`.
- Detener y eliminar también la base local: `docker-compose down -v`.

La API queda disponible en `http://localhost:8080`. `API_PORT` y `POSTGRES_PORT` permiten cambiar los puertos publicados; k6 usa 20 VUs durante 2 minutos por defecto. En instalaciones con el plugin Compose integrado, los mismos comandos aceptan `docker compose` en lugar de `docker-compose`.
