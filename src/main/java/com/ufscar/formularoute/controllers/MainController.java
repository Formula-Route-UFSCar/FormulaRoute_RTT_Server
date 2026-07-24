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

    /**
     * Endpoint para registrar parâmetros de telemetria associados a uma volta (Lap) existente.
     * <p>
     * Este endpoint recebe um JSON contendo o nome da volta (`lap-name`) e vários parâmetros
     * de telemetria (como `temperature`, `speed`, `pressure`, etc.), os quais serão convertidos
     * em objetos `Parameter` e adicionados à volta correspondente.
     * <p>
     * Funcionamento:
     * 1. O JSON enviado deve conter, obrigatoriamente, o campo `lap-name` que identifica a volta
     * no sistema. Além disso, pode conter diversos outros campos que representam parâmetros de
     * telemetria.
     * <p>
     * 2. O sistema faz a busca da volta (`Lap`) no banco de dados com base no nome informado em
     * `lap-name`.
     * <p>
     * 3. Para cada chave-valor do JSON (exceto `lap-name`), é criado um objeto `Parameter`, onde
     * a chave (key) será o nome do parâmetro e o valor (value) será o valor de telemetria
     * correspondente. O timestamp (`added`) é gerado automaticamente no momento da inserção.
     * <p>
     * 4. Todos os parâmetros são então adicionados à lista de parâmetros da volta encontrada, e
     * a volta é salva novamente no banco de dados com os novos dados.
     * <p>
     * 5. Se a volta não for encontrada no banco de dados, é retornada uma mensagem de erro com
     * status 404 (Not Found). Caso o JSON esteja mal formatado ou não contenha o campo
     * `lap-name`, uma resposta de erro 400 (Bad Request) é enviada.
     * <p>
     * Exemplo de JSON de entrada:
     * {
     * "lap-name": "Volta 1",
     * "temperature": "75",
     * "speed": "120",
     * "pressure": "30"
     * }
     * <p>
     * Respostas possíveis:
     * - 200 OK: Se a volta foi encontrada e os parâmetros foram registrados com sucesso.
     * Corpo da resposta: "Lap and parameters registered successfully."
     * <p>
     * - 404 Not Found: Se a volta com o nome fornecido não foi encontrada no banco de dados.
     * Corpo da resposta: "Lap not found."
     * <p>
     * - 400 Bad Request: Se o campo `lap-name` estiver ausente no JSON.
     * Corpo da resposta: "Missing lap-name in the request."
     * <p>
     * - 500 Internal Server Error: Se ocorrer algum erro durante o processamento da solicitação.
     *
     * @param object JSON contendo o nome da volta e os parâmetros de telemetria.
     * @return ResponseEntity com o status da operação (sucesso ou erro).
     */
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

/**
     * Endpoint para retornar todas as Laps (voltas) salvas no sistema.
     *
     * Exemplo de requisição:
     * GET /main/laps
     *
     * Respostas possíveis:
     * - 200 OK: Retorna a lista completa de Laps em formato JSON.
     * - 404 Not Found: Se não houver nenhuma volta salva.
     * - 500 Internal Server Error: Em caso de falha no servidor.
     */
    @GetMapping("/laps")
    public ResponseEntity<?> getAllLaps() {
        try {
            List<Lap> laps = lapRepository.findAll();

            if (!laps.isEmpty()) {
                return ResponseEntity.ok(laps);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No laps found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred.");
        }
    }

    /**
     * Endpoint para retornar a última Lap (volta) registrada no sistema.
     *
     * Exemplo de requisição:
     * GET /main/laps/latest
     *
     * Respostas possíveis:
     * - 200 OK: Retorna o objeto JSON da última Lap criada.
     * - 404 Not Found: Se não houver nenhuma volta salva.
     * - 500 Internal Server Error: Em caso de falha no servidor.
     */
    @GetMapping("/laps/latest")
    public ResponseEntity<?> getLatestLap() {
        try {
            List<Lap> laps = lapRepository.findAll();

            if (!laps.isEmpty()) {
                // Como o findAll() geralmente retorna na ordem de inserção do banco,
                // pegamos o último elemento da lista para garantir a Lap mais recente.
                Lap latestLap = laps.get(laps.size() - 1);
                return ResponseEntity.ok(latestLap);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No laps found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred.");
        }
    }
    
}
