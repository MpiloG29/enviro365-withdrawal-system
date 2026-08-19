# ---- Build stage ----
# Uses the full Maven+JDK image only to compile and package; none of this layer ends up in the final image.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Dependencies resolved from just the POM first, so this layer is cached and skipped on rebuilds where only
# application source changed - only a pom.xml edit invalidates it.
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
# Tests already run via `mvn test` in CI/local dev; skipped here so the image builds only what it needs to
# run (a jar), not re-executes the whole suite on every image build.
RUN mvn -q clean package -DskipTests

# ---- Runtime stage ----
# JRE, not the full JDK - nothing at runtime compiles anything, so the compiler/build tooling in the build
# stage's image would just be unused weight in the image that actually gets deployed.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Activates application-prod.properties (H2 console disabled) - deliberately baked into the image rather than
# left to be set in Render's dashboard, so "deployed" always means "hardened" even if that env var is
# forgotten when the service is created.
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
