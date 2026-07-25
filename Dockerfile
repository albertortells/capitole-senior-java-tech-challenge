FROM gradle:8.5.0-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/project
WORKDIR /home/gradle/project
RUN gradle clean build

# JRE (not JDK) is enough at runtime: same JVM, smaller image, nothing else needs
# compiling here.
FROM eclipse-temurin:21-jre
WORKDIR /app
# Exact filename, not a wildcard: build/libs/ contains exactly one artifact (the
# Shadow fat jar named app.jar; the default thin "jar" task is disabled in
# build.gradle), but pinning the name avoids ever breaking again if that changes.
COPY --from=build /home/gradle/project/build/libs/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]