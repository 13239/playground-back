# syntax=docker/dockerfile:1

# Gradle Wrapper와 Java 25로 Linux에서 실행 JAR만 생성한다.
FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /build
COPY gradle ./gradle
# Jackson 3용 Builder 애너테이션을 생성하는 기존 Lombok 설정도 함께 전달한다.
COPY gradlew build.gradle settings.gradle lombok.config ./
# Windows에서 전달한 Wrapper의 줄바꿈도 Linux 형식으로 정리한다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY src/main ./src/main
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar && cp build/libs/*.jar /build/application.jar

FROM eclipse-temurin:25-jre-noble AS runtime
WORKDIR /app
RUN groupadd --gid 10001 playground && useradd --uid 10001 --gid playground --no-create-home playground
RUN mkdir -p /app/login-audit && chown 10001:10001 /app/login-audit
COPY --from=builder --chown=10001:10001 /build/application.jar ./application.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 9080
# 프로필·DB·JWT 값은 이미지에 고정하지 않고 Compose 실행 환경에서 공급한다.
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
