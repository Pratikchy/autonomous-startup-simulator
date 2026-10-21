# =====================================================================
# Autonomous Virtual Startup Simulator - Multi-stage Production Dockerfile
# Java 25 (Temurin) + Node.js 22 (for isolated startup workspace builds)
# =====================================================================

# Stage 1: Install frontend dependencies (shared by generated workspaces)
FROM node:22-bookworm-slim AS frontend-deps
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci

# Stage 2: Compile Spring Boot backend with Java 25
FROM maven:3.9-eclipse-temurin-25 AS backend-build
WORKDIR /app
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
RUN mvn -f backend/pom.xml clean package -DskipTests

# Stage 3: Runtime container with Java 25 + Node.js 22
FROM eclipse-temurin:25-jre AS runtime

# Install Node.js & npm (required by AppBuildService to compile Vite apps in workspaces)
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy compiled backend jar and pre-cached frontend node_modules
COPY --from=backend-build /app/backend/target/startup-simulator-0.1.0.jar /app/app.jar
COPY --from=frontend-deps /app/frontend/node_modules /app/frontend/node_modules

# Create workspace and data directories
RUN mkdir -p /app/data /app/workspaces

ENV PORT=8080
ENV APP_WORKSPACE_ROOT=/app/workspaces
ENV JAVA_TOOL_OPTIONS=""
EXPOSE 8080

CMD ["sh", "-c", "java $JAVA_TOOL_OPTIONS -Dserver.port=${PORT:-8080} -Dapp.frontend.node-modules=/app/frontend/node_modules -jar /app/app.jar"]
