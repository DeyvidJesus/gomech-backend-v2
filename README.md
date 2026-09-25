# GoMech Backend

[![CI](https://github.com/DeyvidJesus/gomech-backend-v2/actions/workflows/ci.yml/badge.svg)](https://github.com/DeyvidJesus/gomech-backend-v2/actions/workflows/ci.yml)

API REST em Java 21 e Spring Boot 3 para as regras de negócio da plataforma. O backend é um monólito modular: cada domínio tem suas camadas e os módulos se integram por contratos públicos ou eventos.

## Executar

Para subir a stack completa, siga o [guia do ambiente local do repositório principal](https://github.com/DeyvidJesus/gomech/blob/master/docs/guias/ambiente-local.md).

Para executar a API localmente, com Docker Compose v2 e JDK 21 instalados:

```bash
docker compose up -d postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

A API fica em `http://localhost:8080`; o health check fica em `/actuator/health` e a documentação HTTP em `/swagger-ui.html`.

Testes: `./mvnw test` roda os testes unitários e as regras de arquitetura (ArchUnit); `./mvnw verify` inclui os testes de integração (`*IT`), que sobem PostgreSQL com Testcontainers e exigem Docker. A CI executa `verify` a cada push e pull request.

## Organização

- `src/main/java/com/gomech/api/core/`: segurança, tenancy, autorização, auditoria, eventos e contratos transversais.
- `src/main/java/com/gomech/api/modules/`: domínios de negócio, cada um dividido por responsabilidade.
- `src/main/resources/db/migration/`: migrations versionadas pelo Flyway. Mudanças de schema devem entrar em uma nova migration.
- `src/test/`: testes unitários, de arquitetura e de integração.

Perfis Spring: `local`, `dev`, `staging` e `prod`. O ambiente implantado usa GCP; a infraestrutura está documentada no repositório principal.

## Segredos

Somente o perfil `local` tem valores padrão para `JWT_SECRET` e `GOMECH_AI_SERVICE_SECRET`, e esses valores são públicos. Nos demais perfis, as duas variáveis precisam vir do ambiente. A aplicação não sobe quando `JWT_SECRET` falta, tem menos de 256 bits depois de decodificado ou usa um valor público de desenvolvimento. Para gerar um segredo: `openssl rand -hex 32`.

## Referências

- [Arquitetura do backend](https://github.com/DeyvidJesus/gomech/blob/master/docs/BACKEND_ARCHITECTURE.md)
- [ADRs](https://github.com/DeyvidJesus/gomech/blob/master/docs/adr/README.md)
