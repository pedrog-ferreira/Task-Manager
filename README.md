# Task-Manager

## Ambiente local (Spring Boot 3 + Java 23 + PostgreSQL)

### Ficheiros criados
- `docker-compose.yml`
- `.env` (local)
- `.env.example` (template versionado)
- `src/main/resources/application.yml`
- `pom.xml`

## Como arrancar

1. Ajusta as variáveis no ficheiro `.env`.
   - Dica: começa com `cp .env.example .env`.
2. Arranca só a base de dados:
   ```bash
   docker compose up -d
   ```
3. Arranca base de dados + pgAdmin:
   ```bash
   docker compose --profile pgadmin up -d
   ```

## O que meter no `.gitignore`

Mantém `.env` e outros ficheiros locais fora do Git (já configurado):

```gitignore
.env
.env.local
```

## Explicação do `docker-compose.yml`

### `services.postgres`
- `image: postgres:16-alpine`: PostgreSQL 16 numa imagem leve.
- `environment`: define nome da BD, utilizador e password via variáveis (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`).
- `ports`: expõe a porta `5432` do container para o host.
- `volumes`: usa `postgres_data` para persistir dados.
- `healthcheck`: executa `pg_isready` para confirmar quando a BD está realmente pronta para aceitar ligações.

### `services.pgadmin`
- Serviço opcional para interface gráfica.
- `profiles: ["pgadmin"]`: só arranca quando pedes explicitamente o profile `pgadmin`.
- `depends_on ... condition: service_healthy`: espera que o PostgreSQL esteja saudável antes de iniciar.

### `volumes`
- `postgres_data` e `pgadmin_data` são **volumes nomeados** geridos pelo Docker.

#### Volume nomeado vs bind mount
- **Volume nomeado** (`postgres_data:/var/lib/postgresql/data`):
  - gerido pelo Docker;
  - mais portátil entre sistemas;
  - ideal para dados de base de dados.
- **Bind mount** (`./dados:/var/lib/postgresql/data`):
  - aponta para uma pasta concreta no teu disco;
  - útil para editar ficheiros diretamente;
  - maior risco de problemas de permissões/caminhos diferentes por SO.

Para PostgreSQL, normalmente o volume nomeado é a opção mais estável.

### Porque o healthcheck importa
Sem healthcheck, o container pode estar "a correr" mas a BD ainda não pronta. Isso causa falhas intermitentes no arranque da app/migrações. Com `pg_isready`, o ecossistema só considera o serviço pronto quando há resposta real do PostgreSQL.
