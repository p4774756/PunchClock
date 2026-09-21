# Build from monorepo root (PunchClock/). Render: Dockerfile Path = server/Dockerfile
FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app

COPY pom.xml .
COPY shared/pom.xml shared/pom.xml
COPY shared/src shared/src
COPY client/pom.xml client/pom.xml
COPY server/pom.xml server/pom.xml
COPY server/src server/src

RUN mvn -pl server -am package -DskipTests

FROM eclipse-temurin:11-jre-jammy
WORKDIR /app

COPY --from=build /app/server/target/punchclock-server.jar app.jar

ENV PORT=3000
# 容器預設 MaxRAMPercentage=25%，512MB 機器 heap 約 128MB；傳檔已落地串流，仍提高 heap 餘裕。
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
EXPOSE 3000

CMD ["java", "-jar", "app.jar"]
