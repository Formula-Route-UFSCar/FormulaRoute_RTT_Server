FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew clean installDist --no-daemon -x test
RUN echo "=== lib/ ===" && ls -1 build/install/FormulaRoute_RTT_Server/lib/

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/install/FormulaRoute_RTT_Server ./
RUN chmod +x bin/FormulaRoute_RTT_Server
ENTRYPOINT ["./bin/FormulaRoute_RTT_Server"]
