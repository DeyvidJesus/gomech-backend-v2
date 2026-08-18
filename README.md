# GoMech V2 - Backend (Spring Boot)

Este é o backend da plataforma GoMech V2, construído com Java 21 e Spring Boot 3.3+. Ele fornece uma API RESTful para o frontend React.

## Pré-requisitos

Certifique-se de ter instalado em sua máquina:

- **Docker** e **Docker Compose** (Recomendado para rodar a stack completa facilmente)
- **Java 21** (JDK) (Opcional, caso prefira rodar a API fora do Docker)

## Configuração e Execução via Docker (Recomendado)

O fluxo recomendado para desenvolvimento local agora sobe a stack completa a partir da raiz do monorepo.

Na raiz do diretório `gomech`, execute:

```bash
cp .env.example .env
docker compose up --build
```

Isso fará com que:
1. O PostgreSQL seja iniciado.
2. A API Spring Boot seja compilada e executada.
3. O frontend e o serviço de IA também subam no mesmo comando.
4. As migrations do Flyway rodem automaticamente através da API.

A API ficará disponível em `http://localhost:8080`.

O guia completo está em [../docs/STARTUP_GUIDE.md](/home/deyvid/Documents/work/gomech-project/gomech/docs/STARTUP_GUIDE.md).

## Como Rodar Localmente (Fora do Docker)

Caso prefira rodar a API diretamente na sua máquina (usando sua IDE ou Maven) durante o desenvolvimento:

1. **Suba apenas o Banco de Dados:**
   ```bash
   docker compose up -d postgres
   ```
2. **Execute a Aplicação Spring Boot:**
   No Linux/macOS:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```
   No Windows:
   ```cmd
   mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
   ```

A API conectará no banco local em `localhost:5432` e o Flyway aplicará automaticamente as validações de schema na subida.

## Migrations de Banco de Dados (Flyway)

A plataforma utiliza o **Flyway** para controle de versão do banco de dados de maneira determinística. 
Os scripts SQL estão localizados em `src/main/resources/db/migration/`. 

**Aviso:** O modo `ddl-auto: update` do Hibernate está **desativado** por design. 
Todas as mudanças estruturais no banco (novas tabelas, colunas, índices) **devem obrigatoriamente** ser feitas através da criação de novos scripts SQL versionados (ex: `V2__Add_Column.sql`) nessa pasta.

## Perfis de Configuração

O projeto está otimizado para múltiplos ambientes operacionais:
- `local` (Padrão para desenvolvimento local com `application-local.yml`)
- `dev`
- `staging`
- `prod` (Usado em produção no GCP Cloud Run conectando no Cloud SQL via Cloud SQL Auth Proxy)

Em ambiente local, senhas de banco e variáveis do JWT já vêm preenchidas nos arquivos `.yml` para funcionar de imediato (Plug & Play) sem a necessidade de definir dezenas de variáveis de ambiente.
