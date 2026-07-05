package com.velsis.speed_violation_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    OpenAPI speedViolationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Speed Violation Service API TESTESTESTESTESTESTE")
                        .version("1.0.0")
                        .description("""
                                Microserviço REST para apuração de infrações por **excesso de velocidade** \
                                (prova prática Velsis).

                                ## Como testar no Swagger
                                1. Suba o PostgreSQL: `docker compose up -d`
                                2. Inicie a aplicação: `./mvnw spring-boot:run`
                                3. Use **Try it out** nos endpoints abaixo — cada operação traz exemplos prontos.

                                ## Fluxo sugerido para o recrutador
                                1. **POST /evaluate** com o exemplo *Com infração* → grava a infração
                                2. **GET /violations** com a mesma placa → lista o registro persistido
                                3. **POST /evaluate** com *Sem infração* → não persiste (`hasViolation: false`)
                                4. Teste erros 400 com os exemplos de placa inválida ou header ausente

                                ## Header obrigatório
                                Todas as apurações exigem `x-origin` (case-sensitive): `FIXED`, `MOBILE` ou `HANDHELD`.

                                ## Regras resumidas
                                - Tolerância: −7 km/h (limite ≤ 100) ou −7% truncado (limite > 100)
                                - Gravidade: até 20% `MEDIUM`, até 50% `SERIOUS`, acima `VERY_SERIOUS`
                                - Apenas infrações (`hasViolation: true`) são persistidas
                                """)
                        .contact(new Contact()
                                .name("Velsis — Prova Prática Backend Java")
                                .url("https://github.com/oswaldoschermach/speed-violation-service"))
                        .license(new License()
                                .name("Uso educacional / prova técnica")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Ambiente local"),
                        new Server()
                                .url("/")
                                .description("Servidor atual (relativo)")))
                .tags(List.of(
                        new Tag()
                                .name("Infrações")
                                .description("""
                                        Apuração de leituras de velocidade e consulta de infrações \
                                        registradas por placa.""")))
                .components(new Components());
    }
}
