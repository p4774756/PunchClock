# Build from monorepo root (PunchClock/). Render: Dockerfile Path = server/Dockerfile
FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app

COPY pom.xml .
COPY shared/pom.xml shared/pom.xml
COPY shared/src shared/src
COPY client/pom.xml client/pom.xml
COPY client/src client/src
COPY server/pom.xml server/pom.xml
COPY server/src server/src

# 同時打包桌面端，image 內帶著這次 commit 的 client jar，心跳才能通知更新。
# 略過 git-commit-id：建置環境不一定有 .git，更新比對用的是原始碼摘要。
RUN mvn -pl client,server -am package -DskipTests -P '!git-commit-time' \
    && mkdir -p /tmp/client-release \
    && cd /tmp/client-release \
    && jar xf /app/client/target/punchclock-client-standalone.jar version.properties \
    && grep -E '^sourceDigest=[0-9a-f]{64}$' version.properties

FROM eclipse-temurin:11-jre-jammy
WORKDIR /app

COPY --from=build /app/server/target/punchclock-server.jar app.jar
COPY --from=build /app/client/target/punchclock-client-standalone.jar client/punchclock-client-standalone.jar

ENV PORT=3000
# 512MB 機器：heap 約 256MB，其餘留給 metaspace／執行緒／寫檔時的 page cache，避免整個容器被 OOM kill。
# 傳檔是串流落地，不需要大 heap。
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0"
EXPOSE 3000

# Render Persistent Disk 掛載點（需在 Render Dashboard 設定 1GB disk 掛載到 /var/data）
# 照片與傳檔會自動使用此目錄；本地開發時若目錄不存在則 fallback 到 tmpdir／記憶體
VOLUME ["/var/data"]

CMD ["java", "-jar", "app.jar"]
