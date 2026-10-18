# 后端镜像：多阶段构建（构建层可复用 Maven 缓存，运行层仅含 JRE）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 国内网络环境使用镜像源（云服务器在境外时可删除该层）
RUN mkdir -p /root/.m2 && printf '<?xml version="1.0" encoding="UTF-8"?>\n<settings>\n  <mirrors>\n    <mirror>\n      <id>aliyun</id>\n      <mirrorOf>central</mirrorOf>\n      <url>https://maven.aliyun.com/repository/public</url>\n    </mirror>\n  </mirrors>\n</settings>\n' > /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/crane-backend.jar app.jar
# 时区与容器内临时目录
ENV TZ=Asia/Shanghai JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -sf http://127.0.0.1:8080/api/v1/system/health >/dev/null || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
