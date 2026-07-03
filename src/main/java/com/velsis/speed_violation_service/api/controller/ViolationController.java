package com.velsis.speed_violation_service.api.controller;

import com.velsis.speed_violation_service.api.exception.ApiErrorResponse;
import com.velsis.speed_violation_service.api.mapper.ViolationMapper;
import com.velsis.speed_violation_service.api.request.EvaluateViolationRequest;
import com.velsis.speed_violation_service.api.response.EvaluateViolationResponse;
import com.velsis.speed_violation_service.api.response.StoredViolationResponse;
import com.velsis.speed_violation_service.api.validation.ValidLicensePlate;
import com.velsis.speed_violation_service.config.OpenApiExamples;
import com.velsis.speed_violation_service.domain.model.CaptureOrigin;
import com.velsis.speed_violation_service.domain.service.ViolationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Infrações")
@RestController
@RequestMapping("/api/v1/violations")
@Validated
public class ViolationController {

    private final ViolationService violationService;

    public ViolationController(ViolationService violationService) {
        this.violationService = violationService;
    }

    @Operation(
            summary = "Apurar infração por excesso de velocidade",
            description = """
                    Recebe uma leitura de velocidade, aplica a tolerância legal, calcula o excesso \
                    e classifica a gravidade conforme o CTB.

                    **Persistência:** apenas leituras com `hasViolation: true` são gravadas.

                    **Dica:** use o exemplo *Com infração (exemplo da prova)* e depois consulte \
                    a mesma placa em GET /violations.
                    """,
            parameters = {
                    @Parameter(
                            name = "x-origin",
                            in = ParameterIn.HEADER,
                            required = true,
                            description = "Origem da captura (case-sensitive).",
                            schema = @Schema(implementation = CaptureOrigin.class),
                            examples = {
                                    @ExampleObject(name = "Equipamento fixo", value = "FIXED"),
                                    @ExampleObject(name = "Equipamento móvel", value = "MOBILE"),
                                    @ExampleObject(name = "Equipamento portátil", value = "HANDHELD")
                            })
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Dados da leitura capturada pelo equipamento.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = EvaluateViolationRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Com infração (exemplo da prova)",
                                            summary = "92 km/h em via de 60 — gravidade SERIOUS",
                                            value = OpenApiExamples.EVALUATE_WITH_VIOLATION_REQUEST),
                                    @ExampleObject(
                                            name = "Sem infração (dentro da tolerância)",
                                            summary = "64 km/h em via de 60 — hasViolation false",
                                            value = OpenApiExamples.EVALUATE_NO_VIOLATION_REQUEST),
                                    @ExampleObject(
                                            name = "Placa formato antigo",
                                            summary = "Placa ABC1234 válida",
                                            value = OpenApiExamples.EVALUATE_LEGACY_PLATE_REQUEST),
                                    @ExampleObject(
                                            name = "Via acima de 100 km/h",
                                            summary = "Tolerância percentual truncada",
                                            value = OpenApiExamples.EVALUATE_HIGHWAY_REQUEST),
                                    @ExampleObject(
                                            name = "Placa inválida (gera 400)",
                                            summary = "Use para testar erro de validação",
                                            value = OpenApiExamples.EVALUATE_INVALID_PLATE_REQUEST)
                            })))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Apuração realizada com sucesso (com ou sem infração).",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = EvaluateViolationResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Com infração",
                                            value = OpenApiExamples.EVALUATE_WITH_VIOLATION_RESPONSE),
                                    @ExampleObject(
                                            name = "Sem infração",
                                            value = OpenApiExamples.EVALUATE_NO_VIOLATION_RESPONSE)
                            })),
            @ApiResponse(
                    responseCode = "400",
                    description = "Erro de validação (placa, velocidades, timestamp, header x-origin, etc.).",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Placa inválida",
                                            value = OpenApiExamples.ERROR_INVALID_LICENSE_PLATE),
                                    @ExampleObject(
                                            name = "Header x-origin ausente",
                                            value = OpenApiExamples.ERROR_INVALID_ORIGIN),
                                    @ExampleObject(
                                            name = "Timestamp inválido",
                                            value = OpenApiExamples.ERROR_INVALID_CAPTURE_TIMESTAMP),
                                    @ExampleObject(
                                            name = "Velocidade medida inválida",
                                            value = OpenApiExamples.ERROR_INVALID_MEASURED_SPEED)
                            })),
            @ApiResponse(
                    responseCode = "409",
                    description = "Captura já registrada (placa + equipamento + timestamp duplicados).",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Captura duplicada",
                                    value = OpenApiExamples.ERROR_DUPLICATE_VIOLATION)))
    })
    @PostMapping("/evaluate")
    public EvaluateViolationResponse evaluate(
            @RequestHeader("x-origin") CaptureOrigin origin,
            @Valid @RequestBody EvaluateViolationRequest request) {
        return violationService.evaluate(ViolationMapper.toCommand(request, origin));
    }

    @Operation(
            summary = "Consultar infrações por placa",
            description = """
                    Retorna todas as infrações **persistidas** para a placa informada, \
                    ordenadas da mais recente para a mais antiga.

                    Retorna lista vazia `[]` se não houver registros.

                    **Dica:** execute antes um POST /evaluate com infração usando a mesma placa.
                    """)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de infrações (pode ser vazia).",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = StoredViolationResponse.class)),
                            examples = {
                                    @ExampleObject(
                                            name = "Com registros",
                                            value = OpenApiExamples.STORED_VIOLATIONS_RESPONSE),
                                    @ExampleObject(
                                            name = "Sem registros",
                                            value = OpenApiExamples.STORED_VIOLATIONS_EMPTY_RESPONSE)
                            })),
            @ApiResponse(
                    responseCode = "400",
                    description = "Placa ausente ou em formato inválido.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Placa inválida",
                                    value = OpenApiExamples.ERROR_INVALID_LICENSE_PLATE)))
    })
    @GetMapping
    public List<StoredViolationResponse> findByLicensePlate(
            @Parameter(
                    description = "Placa do veículo (antigo ABC1234 ou Mercosul ABC1D23).",
                    required = true,
                    example = "ABC1D23")
            @RequestParam @NotBlank(message = "License plate is required") @ValidLicensePlate String licensePlate) {
        return violationService.findByLicensePlate(licensePlate);
    }
}
