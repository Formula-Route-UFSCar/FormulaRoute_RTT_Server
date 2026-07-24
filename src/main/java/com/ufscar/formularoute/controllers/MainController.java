package com.ufscar.formularoute.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ufscar.formularoute.dto.Lap;
import com.ufscar.formularoute.dto.Parameter;
import com.ufscar.formularoute.repository.LapRepository;
import com.ufscar.formularoute.request.LapRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/main")
@CrossOrigin(origins = "*") 
public class MainController {

    @Autowired
    private LapRepository lapRepository;

    @PostMapping("/parameter/register") 
    public ResponseEntity<String> registerLap(@RequestBody String object) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode requestBody = objectMapper.readTree(object);

            if (requestBody.has("lap-name")) {
                String lapName = requestBody.get("lap-name").asText();
                System.out.println("Lap Name: " + lapName);

                Optional<Lap> lapOptional = lapRepository.findByName(lapName);
                Lap lap;

                // 3. CORREÇÃO: Se a Lap não existir, cria automaticamente em vez de retornar 404
                if (lapOptional.isPresent()) {
                    lap = lapOptional.get();
                } else {
                    lap = new Lap();
                    lap.setName(lapName);
                    // Salva a nova Lap vazia primeiro para poder associar os parâmetros
                    lap = lapRepository.save(lap); 
                    System.out.println("Nova Lap criada automaticamente: " + lapName);
                }

                List<Parameter> parameters = new ArrayList<>();

                requestBody.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue().asText();

                    if (!key.equals("lap-name")) {
                        Parameter parameter = new Parameter();
                        parameter.setKey(key);
                        parameter.setValue(value);
                        parameter.setAdded(ZonedDateTime.now().withZoneSameInstant(ZoneId.of("America/Sao_Paulo")));
                        parameters.add(parameter);
                    }
                });

                lap.getParameters().addAll(parameters);
                lapRepository.save(lap);

                return ResponseEntity.ok("Lap and parameters registered successfully.");
                
            } else {
                return ResponseEntity.badRequest().body("Missing lap-name in the request.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred.");
        }
    }

    @PostMapping("/create")
    public ResponseEntity<?> createLap(@RequestBody LapRequest lapRequest) {
        try {
            if (lapRequest.getName() == null || lapRequest.getName().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Lap name is required.");
            }

            Lap newLap = new Lap();
            newLap.setName(lapRequest.getName());
            Lap savedLap = lapRepository.save(newLap);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedLap);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred.");
        }
    }

    @GetMapping("/{lapName}/parameter/{key}")
    public ResponseEntity<?> getParameterValues(@PathVariable String lapName, @PathVariable String key) {
        try {
            Optional<Lap> lapOptional = lapRepository.findByName(lapName);

            if (lapOptional.isPresent()) {
                Lap lap = lapOptional.get();
                List<Parameter> matchingParameters = lap.getParameters().stream()
                        .filter(param -> param.getKey().equals(key))
                        .collect(Collectors.toList());
                if (!matchingParameters.isEmpty())
                    return ResponseEntity.ok(matchingParameters);
                else return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Parameter with key [" + key + "] not found in this lap.");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Lap not found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred.");
        }
    }

    @GetMapping("/{lapName}/parameters")
    public ResponseEntity<?> getLapParameters(@PathVariable String lapName) {
        try {
            Optional<Lap> lapOptional = lapRepository.findByName(lapName);

            if (lapOptional.isPresent()) {
                Lap lap = lapOptional.get();
                List<Parameter> parameters = lap.getParameters();

                if (!parameters.isEmpty()) {
                    return ResponseEntity.ok(parameters);
                } else {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No parameters found for this lap.");
                }
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Lap not found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred.");
        }
    }
}
