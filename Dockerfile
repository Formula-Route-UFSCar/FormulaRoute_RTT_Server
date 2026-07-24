FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew installDist --no-daemon -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/install/FormulaRoute_RTT_Server/lib ./lib
EXPOSE 8080
ENTRYPOINT ["java","-cp","/app/lib/*","com.ufscar.formularoute.Main"]
