# ---------- Build ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline || true
COPY src ./src
RUN mvn -q -DskipTests package

# ---------- Runtime ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ENV TZ=America/New_York
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=build /app/target/*.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"
ENV SERVER_PORT=8081
EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=5 \
  CMD bash -lc "exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT} && exec 3>&-"

ENTRYPOINT ["java","-jar","/app/app.jar"]