# Build from monorepo root (PunchClock/). Render: Dockerfile Path = server/Dockerfile
# 經穴圖在這個階段編譯，最後的執行映像只留下靜態網站。
FROM node:22-bookworm AS atlas
WORKDIR /src
COPY atlas-src/tcm-3d tcm-3d
RUN cd tcm-3d && npm ci && npm run build:punchclock

FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app

COPY pom.xml .
COPY shared/pom.xml shared/pom.xml
COPY shared/src shared/src
# 根 pom 仍列出 client 模組，必須留下 pom 讓 Maven 能讀 reactor。
# 桌面端改從 GitHub Release 下載，所以不複製 client 原始碼、也不打進映像。
COPY client/pom.xml client/pom.xml
COPY server/pom.xml server/pom.xml
COPY server/src server/src

# -pl shared,server：不要 also-make client（沒有原始碼，且會把 Playwright 打進多餘的 shade jar）。
# -P !client-tests：不要解析 server 對 punchclock-client 的測試依賴（-Dmaven.test.skip 仍會解析）。
# -Dmaven.test.skip：略過測試編譯。
RUN mvn -pl shared,server package -Dmaven.test.skip=true -P '!git-commit-time,!client-tests'

FROM eclipse-temurin:11-jre-jammy
# 飲品牆把 PowerPoint 轉成 PDF。同時只轉一份。Pages 只取檔內預覽，不經 LibreOffice。
RUN apt-get update \
    && apt-get install -y --no-install-recommends libreoffice-impress fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app

COPY --from=build /app/server/target/punchclock-server.jar app.jar
COPY --from=atlas /src/tcm-3d/dist /app/atlas/tcm-3d

ENV PORT=3000
ENV ATLAS_ROOT=/app/atlas
# 512MB 機器：heap 約 256MB，其餘留給 metaspace／執行緒／寫檔時的 page cache，避免整個容器被 OOM kill。
# 傳檔是串流落地，不需要大 heap。
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0"
EXPOSE 3000

# Render Persistent Disk 掛載點（需在 Render Dashboard 設定 1GB disk 掛載到 /var/data）
# 照片與傳檔會自動使用此目錄；本地開發時若目錄不存在則 fallback 到 tmpdir／記憶體
VOLUME ["/var/data"]

CMD ["java", "-jar", "app.jar"]
