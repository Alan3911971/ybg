# ============================================================================
# ibigou-blindbox 生产镜像（多阶段构建）
# 构建：docker build -t ibigou-blindbox:1.0.0 .
# 运行：见 docs/DEPLOY-PROD.md（需注入 IBIGOU_DB_* 环境变量）
# ============================================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -q dependency:go-offline -DskipTests
COPY backend/src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S ibigou && adduser -S ibigou -G ibigou
WORKDIR /app
COPY --from=build /build/target/ibigou-blindbox-*.jar app.jar
COPY db/schema.sql /app/schema.sql
RUN chown -R ibigou:ibigou /app
USER ibigou
EXPOSE 8085
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
