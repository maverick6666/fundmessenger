# ── Stage 1: Build ──
FROM gradle:8.11-jdk21-alpine AS build
WORKDIR /app
COPY backend/ .
RUN gradle bootJar --no-daemon -x test

# ── Stage 2: Runtime ──
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 한국어 PDF 처리를 위한 폰트
RUN apk add --no-cache fontconfig ttf-dejavu

COPY --from=build /app/build/libs/*.jar app.jar

# Cloud Run은 PORT 환경변수를 자동 주입 (기본 8080)
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
