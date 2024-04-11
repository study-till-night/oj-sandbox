#FROM maven:3.5-jdk-8-alpine as builder
## Copy local code to the container image.
#ENV PATH /server
#ENV APP_PATH  /server/target/premade_dished_system.jar
#WORKDIR $PATH
#COPY pom.xml .
#COPY src ./src
## Build a release artifact.
#RUN mvn package -DskipTests
## Run the web service on container startup.
#CMD ["java","-jar","echo $APP_PATH","--spring.profiles.active=prod"]

FROM openjdk:8-jdk-alpine
#将所有jar包合并
COPY *.jar /app.jar
#暴露后端端口号
EXPOSE 8088
#执行jar包
ENTRYPOINT ["java","-jar","app.jar","--spring.profiles.active=prod"]