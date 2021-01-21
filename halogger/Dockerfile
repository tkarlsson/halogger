FROM openjdk:8-jdk-alpine

MAINTAINER Tobias Karlsson <tobbe@flexiverse.eu>

VOLUME /tmp
ARG JAR_FILE
ADD ${JAR_FILE} app.jar

ENV JAVA_OPTS=""

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]

