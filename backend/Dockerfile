FROM maven:3.9-eclipse-temurin-21

WORKDIR /code

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src

RUN mvn package -DskipTests -q

EXPOSE 8000

CMD ["java", \
     "--add-opens", "java.base/java.lang=ALL-UNNAMED", \
     "--add-opens", "java.base/java.io=ALL-UNNAMED", \
     "--add-opens", "java.base/java.util=ALL-UNNAMED", \
     "-jar", "target/solomillo.jar"]
