# Task Manager

Aplicação de gestão de tarefas ("Gestor de Tarefas") — backend em **Spring Boot 3** / **Java 23**, com **PostgreSQL 16** como base de dados, migrações geridas por **Flyway** e ambiente local orquestrado com **Docker Compose**.

Este README documenta o ambiente de desenvolvimento local: como o arrancar, e — mais importante — **o que cada peça faz e porquê**, para não seres só um copy-paste de comandos.

---

## 1. Stack e ficheiros

| Ficheiro | Papel |
|---|---|
| `docker-compose.yml` | Sobe PostgreSQL (e, opcionalmente, pgAdmin) em containers, para dev local. |
| `.env` | Valores reais das variáveis usadas pelo `docker-compose.yml` e pela app. **Não é versionado.** |
| `.env.example` | Template do `.env`, versionado, sem segredos reais. |
| `src/main/resources/application.yml` | Configuração do Spring Boot (datasource, JPA, Flyway, perfis `dev`/`prod`). |
| `src/main/java/com/taskmanager/` | Código da app — `TaskManagerApplication` + pacotes por camada (ver secção 8.1). |
| `pom.xml` | Dependências Maven (Web, JPA, driver PostgreSQL, Flyway, Validation, Lombok, DevTools). |

Stack de versões:
- Java **23**
- Spring Boot **3.3.5**
- PostgreSQL **16** (imagem `alpine`)
- Flyway **core** (versão gerida pelo `spring-boot-starter-parent`)

---

## 2. Pré-requisitos

- Docker + Docker Compose (v2, comando `docker compose`, sem hífen)
- JDK 23
- Maven (ou o wrapper `./mvnw`, se vieres a adicionar um)

---

## 3. Arranque rápido

```bash
# 1. Cria o teu .env local a partir do template (só na primeira vez)
cp .env.example .env

# 2. Arranca só a base de dados
docker compose up -d

# 3. Confirma que está saudável
docker compose ps
```

Quando `docker compose ps` mostrar o `postgres` como `healthy`, a BD está pronta para aceitar ligações reais (ver secção 6 sobre o healthcheck).

Para arrancar a BD **+ pgAdmin** (interface gráfica web, em `http://localhost:5050`):

```bash
docker compose --profile pgadmin up -d
```

Para parar tudo (mantendo os dados):

```bash
docker compose down
```

Para parar e **apagar também os dados** (reset total da BD):

```bash
docker compose down -v
```

Com a BD a correr, arranca a aplicação Spring Boot (perfil `dev` por omissão, ver `.env`):

```bash
mvn spring-boot:run
```

---

## 4. `docker-compose.yml`, bloco a bloco

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: task-manager-postgres
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "${POSTGRES_PORT}:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s
```

- **`image: postgres:16-alpine`** — imagem oficial do Postgres 16, variante `alpine` (base Linux mínima). Mais pequena e rápida de puxar/arrancar do que a imagem "completa" (`debian`-based), sem perder funcionalidade para desenvolvimento.
- **`environment`** — a imagem oficial do Postgres lê estas três variáveis no *primeiro* arranque (quando o volume ainda está vazio) para criar a BD, o utilizador dono e a password. `${VAR}` sem valor por omissão é intencional: **nenhuma credencial fica hardcoded no ficheiro versionado**, tem mesmo de vir do `.env` (que não é commitado). Se o `.env` não existir ou faltar a variável, o Compose avisa `variable is not set` em vez de arrancar silenciosamente com uma password previsível.
- **`ports: "5432:5432"`** — mapeia a porta 5432 do *host* (a tua máquina) para a porta 5432 *dentro* do container. É o que permite ao teu cliente SQL, ao pgAdmin fora do Docker, ou à app Spring Boot correndo fora do Docker, ligarem-se a `localhost:5432`. Também parametrizada por `.env`, útil se já tiveres um Postgres local a ocupar a 5432.
- **`volumes: postgres_data:/var/lib/postgresql/data`** — ver secção 5, é o mecanismo de persistência dos dados.
- **`healthcheck`** — ver secção 6.

```yaml
  pgadmin:
    image: dpage/pgadmin4:8
    container_name: task-manager-pgadmin
    profiles: ["pgadmin"]
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      PGADMIN_DEFAULT_EMAIL: ${PGADMIN_DEFAULT_EMAIL}
      PGADMIN_DEFAULT_PASSWORD: ${PGADMIN_DEFAULT_PASSWORD}
    ports:
      - "${PGADMIN_PORT}:80"
    volumes:
      - pgadmin_data:/var/lib/pgadmin
```

- **`profiles: ["pgadmin"]`** — isto é o que torna o pgAdmin **opcional**. Um serviço com `profiles` definido só arranca se invocares esse profile explicitamente (`docker compose --profile pgadmin up -d`). Um `docker compose up -d` normal (sem `--profile`) ignora-o por completo — arrancas só a BD, mais leve, mais rápido, sem abrir uma UI web que a maior parte das vezes não precisas.
- **`depends_on: postgres: condition: service_healthy`** — o pgAdmin só arranca depois do `healthcheck` do Postgres reportar `healthy`. Sem isto, o pgAdmin podia arrancar antes da BD estar pronta a aceitar ligações (não seria grave — o pgAdmin liga-se sob pedido — mas evita corridas desnecessárias e mensagens de erro confusas se tentares ligar logo a seguir ao arranque).

```yaml
volumes:
  postgres_data:
  pgadmin_data:
```

Declaração dos dois volumes nomeados usados acima — ver secção 5.

---

## 5. Volume nomeado vs. bind mount

Ambos servem para os dados **sobreviverem** a um `docker compose down` / restart do container (sem persistência, perderias a BD toda de cada vez que o container parasse).

| | Volume nomeado (`postgres_data:/var/lib/postgresql/data`) | Bind mount (`./dados:/var/lib/postgresql/data`) |
|---|---|---|
| Onde vive no disco | Numa área gerida pelo Docker (ex.: `/var/lib/docker/volumes/...` no Linux; dentro da VM do Docker Desktop no Windows/Mac) | Numa pasta concreta do teu projeto/disco, que tu escolhes |
| Quem gere o ciclo de vida | O Docker (criar, listar `docker volume ls`, apagar `docker volume rm`) | Tu, manualmente, como qualquer pasta |
| Portabilidade entre SOs | Alta — não depende de paths nem permissões do host | Baixa — permissões Unix vs. Windows, paths diferentes, podem dar problemas |
| Acesso direto aos ficheiros | Não é suposto mexeres lá diretamente | Podes abrir a pasta no explorador/editor a qualquer momento |
| Caso de uso típico | Dados de BD, filas, caches — coisas que só a app dentro do container deve tocar | Código-fonte com hot-reload, ficheiros de configuração que queres editar do host |

**Porque escolhemos volume nomeado aqui:** os ficheiros internos do PostgreSQL (`base/`, `pg_wal/`, etc.) são geridos inteiramente pelo motor da BD — não há razão nem vantagem em editá-los à mão, e um bind mount introduz só risco (permissões erradas em Windows/WSL, path com espaços como este próprio projeto tem, etc.) sem benefício real. Para uma BD, o volume nomeado é a opção standard e mais estável.

Comandos úteis:
```bash
docker volume ls                     # lista os volumes nomeados existentes
docker volume inspect postgres_data  # mostra onde vive fisicamente
```

---

## 6. Porque o `healthcheck` importa

Um container "a correr" (`docker compose ps` mostra `Up`) **não é o mesmo** que "pronto a aceitar ligações". O processo do Postgres arranca, mas passa por uma fase de inicialização (criar a BD na primeira vez, recuperar o WAL, etc.) antes de conseguir responder a queries.

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 10s
```

- `pg_isready` é a ferramenta oficial do Postgres para perguntar "aceitas ligações agora?" — devolve sucesso/erro, sem precisares de autenticação completa.
- `interval: 10s` — corre este teste a cada 10 segundos.
- `timeout: 5s` — se o teste não responder em 5s, conta como falhado.
- `retries: 5` — precisa de falhar 5 vezes seguidas para o Docker marcar o serviço como `unhealthy`.
- `start_period: 10s` — período de "tolerância" inicial em que falhas não contam para o `retries` (dá tempo ao Postgres de arrancar sem seres penalizado logo de início).

**Porque importa na prática:**
1. **`depends_on: condition: service_healthy`** (usado pelo pgAdmin acima) só funciona por causa do healthcheck — sem ele, `depends_on` garante apenas que o container do Postgres *arrancou*, não que já aceita ligações. É a diferença entre "o processo existe" e "o serviço responde".
2. Quando ligares a tua app Spring Boot a correr fora do Docker (com `mvn spring-boot:run`), se a tentares arrancar no mesmo instante em que fazes `docker compose up`, sem esperares pelo `healthy`, o Flyway/Hibernate podem falhar a ligar-se na primeira tentativa — erros intermitentes e confusos de "connection refused" que desaparecem se tentares de novo. Verificar `docker compose ps` (ou script de espera) antes de arrancar a app evita esta falsa alarme.
3. Ferramentas de orquestração (CI, scripts de arranque, um futuro `docker-compose` com a própria app como serviço) usam exatamente este sinal para decidir quando é seguro avançar para o passo seguinte.

---

## 7. `.env` / `.env.example`

```env
POSTGRES_DB=example
POSTGRES_USER=example
POSTGRES_PASSWORD=example
POSTGRES_PORT=example

PGADMIN_DEFAULT_EMAIL=example
PGADMIN_DEFAULT_PASSWORD=example
PGADMIN_PORT=example

DB_HOST=localhost
DB_PORT=5432
SPRING_PROFILES_ACTIVE=dev
```

- `.env.example` é o **template versionado** — mostra que variáveis existem e valores de exemplo plausíveis, sem segredos reais (aqui os valores são só de dev local, mas o princípio mantém-se para produção).
- `.env` é o ficheiro **real**, local, com os teus valores — não deve ir para o Git. Cria-se uma vez com `cp .env.example .env` e ajusta-se à vontade sem afetar mais ninguém no repositório.
- `DB_HOST` / `DB_PORT` são usadas pelo `application.yml` (a app, fora do Docker, liga-se a `localhost:5432`); `POSTGRES_*` são usadas pelo `docker-compose.yml` (dentro do Docker). Estarem separadas permite, por exemplo, mudar a porta exposta pelo Postgres sem teres de tocar na configuração da app, ou vice-versa.

### O que meter no `.gitignore`

```gitignore
.env
.env.local
!.env.example
```

- `.env` / `.env.local` — nunca versionar ficheiros com credenciais reais (mesmo sendo dev local, é o hábito correto — em produção estas variáveis podem conter passwords a sério).
- `!.env.example` — o `!` **anula** uma exclusão anterior; garante que o template, mesmo estando dentro do padrão `.env*`, continua a ser versionado. Isto já está configurado no `.gitignore` do projeto.

---

## 8. `application.yml`

```yaml
spring:
  application:
    name: task-manager
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${POSTGRES_DB:gestortarefas}}
    username: ${SPRING_DATASOURCE_USERNAME:${POSTGRES_USER}}
    password: ${SPRING_DATASOURCE_PASSWORD:${POSTGRES_PASSWORD}}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: ${SERVER_PORT:8080}

---
spring:
  config:
    activate:
      on-profile: dev
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    org.hibernate.orm.jdbc.bind: TRACE

---
spring:
  config:
    activate:
      on-profile: prod
  jpa:
    show-sql: false
```

- **`datasource`** — lê tudo de variáveis de ambiente. Note-se a diferença entre `username`/`password` (sem valor por omissão — têm de vir do `.env`, nunca há uma credencial hardcoded no ficheiro versionado) e `url` (tem defaults para `DB_HOST`/`DB_PORT`/`POSTGRES_DB`, porque um nome de BD ou "localhost" não é informação sensível).
- **`ddl-auto: validate`** — o Hibernate **nunca** cria nem altera o esquema da BD; só verifica se as entidades JPA batem certo com as tabelas existentes, e falha alto e claro se não baterem. Isto força o esquema a ser sempre gerido pelas migrações do Flyway (fonte única da verdade), evitando o clássico problema de "funciona na minha máquina" por o Hibernate ter criado tabelas ligeiramente diferentes em cada ambiente.
- **`flyway.locations: classpath:db/migration`** — o Flyway aplica, por ordem de versão, os scripts `.sql` encontrados em `src/main/resources/db/migration` (ex.: `V1__init.sql`, `V2__add_users.sql`). Corre automaticamente no arranque da app, antes do Hibernate validar o esquema.
- **`server.port`** — porta HTTP da app (Tomcat embutido), configurável por `SERVER_PORT` no `.env`; por omissão `8080`.
- **Perfis `dev` / `prod`** (separados por `---`, sintaxe multi-documento do Spring) — em `dev` liga-se `show-sql`/`format_sql` (queries SQL formatadas na consola) e `logging.level.org.hibernate.orm.jdbc.bind: TRACE` (mostra os *valores* dos parâmetros ligados a cada query — muito verboso, só para depurar); em `prod` fica tudo desligado. O perfil ativo vem de `SPRING_PROFILES_ACTIVE` (no `.env`, atualmente `dev`).

---

## 8.1 Código da app: pacotes e classe principal

```
src/main/java/com/taskmanager/
├── TaskManagerApplication.java   ← @SpringBootApplication, ponto de entrada
├── config/                       ← beans, propriedades, CORS
├── controller/                   ← @RestController — só fala com o service
├── dto/                          ← records de entrada/saída da API (@Valid)
├── entity/                       ← @Entity, mapeadas às migrações Flyway
├── exception/                    ← exceções de domínio + @RestControllerAdvice
├── repository/                   ← JpaRepository<Entidade, Id>
└── service/                      ← lógica de negócio
```

Organização por camada (a mais comum de se ver). Cada pacote tem um `package-info.java` com uma frase a lembrar a responsabilidade daquela camada — útil como documentação viva, e garante que a pasta fica versionada mesmo antes de lá existir alguma classe (o Git não regista diretórios vazios).

Regra a manter à medida que cresce: `controller → service → repository`, sempre nessa direção; nunca um controller a chamar o repository diretamente, nunca uma entidade a sair de um controller para fora (usa sempre um DTO).

---

## 9. Dependências Maven (`pom.xml`)

| Dependência | Para quê |
|---|---|
| `spring-boot-starter-web` | Controllers REST + Tomcat embutido. |
| `spring-boot-starter-data-jpa` | Spring Data JPA + Hibernate como implementação JPA — mapeamento objeto-relacional e repositórios. |
| `spring-boot-starter-validation` | `@Valid`/`@NotBlank` etc. nos DTOs dos controllers. |
| `org.postgresql:postgresql` (scope `runtime`) | Driver JDBC do PostgreSQL — necessário para o datasource e para o Flyway se ligarem à BD. `runtime` porque o código da app nunca importa classes deste driver diretamente, só é preciso em execução. |
| `org.flywaydb:flyway-core` | Motor de migrações Flyway, que corre os scripts de `db/migration` no arranque. |
| `org.flywaydb:flyway-database-postgresql` | Desde o Flyway 10, o suporte a cada BD ficou separado do `flyway-core`. **Sem esta dependência a app falha no arranque** com `Unsupported Database: PostgreSQL`. |
| `lombok` (`optional`) | Menos boilerplate (`@Getter`, `@Builder`, etc.). Excluído do jar final via `spring-boot-maven-plugin` (só é preciso em tempo de compilação). |
| `spring-boot-devtools` (`runtime`, `optional`) | Restart automático da app quando gravas ficheiros. |
| `spring-boot-starter-test` (scope `test`) | JUnit 5 + Mockito + AssertJ, para os testes. |

Deixámos o **Spring Security** de fora de propósito — entra mais tarde; se entrasse agora, todos os endpoints ficavam a devolver `401` enquanto testas o resto.

---

## 10. Bug conhecido (e resolvido): caracteres especiais no caminho do projeto

`mvn spring-boot:run`, corrido na linha de comandos, falhava com:

```
Error: Could not find or load main class com.taskmanager.TaskManagerApplication
Caused by: java.lang.ClassNotFoundException: com.taskmanager.TaskManagerApplication
```

mesmo com a classe compilada e presente em `target/classes`. Diagnóstico confirmado: o `spring-boot-maven-plugin` gera um `@argfile` temporário com o classpath para lançar a app num processo Java separado; nesta máquina, esse mecanismo não lida bem com um caractere especial (`º`) no caminho do projeto, e a entrada do classpath que aponta para `target\classes` fica corrompida — só a tua própria classe deixa de ser encontrada, as bibliotecas externas (que vivem no `.m2`, sem caracteres especiais no caminho) continuam a resolver-se bem, o que ajudou a confirmar a causa.

Confirmámos a causa copiando o projeto para um caminho sem `º` e correndo lá — arrancou sem qualquer alteração ao código. **Solução aplicada:** mover o projeto para um caminho só com ASCII (sem acentos/ordinais). Isto evita a mesma classe de problemas noutras ferramentas (empacotamento do jar, plugins de build, etc.), não é só um paliativo para este comando.

Se voltares a mudar a localização do projeto no futuro, evita acentos, `º`/`ª`, e outros caracteres fora do ASCII básico no caminho — espaços sozinhos não são o problema.

---

## 11. O que falta / próximos passos

O ambiente (BD + configuração + esqueleto do projeto) está pronto e testado — a app já arranca, liga-se ao Postgres via Hikari, corre o Flyway e sobe o Tomcat. Falta ainda:

1. **Primeira migração Flyway** — a pasta `src/main/resources/db/migration` ainda não existe. Sem migrações, o Flyway só cria a tabela de histórico e não faz mais nada (aviso `No migrations found`, não é erro). Com `ddl-auto: validate`, assim que criares a primeira entidade JPA vais precisar de um `V1__init.sql` correspondente.
2. **Entidades/repositórios/controllers/services/DTOs** — o esqueleto de camadas existe (secção 8.1), mas está vazio; falta o domínio real (`Tarefa`, `Utilizador`, etc.).
3. **Variáveis de ambiente no Run/Debug Configuration do IntelliJ** — quando corres a app pelo IntelliJ (não pelo `docker compose`), as variáveis do `.env` não são lidas automaticamente por ele; define-as em `Run → Edit Configurations → Environment variables` com os mesmos valores do teu `.env` (ou instala o plugin "EnvFile" do IntelliJ para apontar diretamente para o ficheiro `.env` e evitar duplicar valores).
4. *(Opcional, mais tarde)* Adicionar o `mvnw`/`mvnw.cmd` (Maven Wrapper) ao repositório, para não depender de teres Maven instalado globalmente.

## 12. Resolução de problemas comuns

- **Porta 5432 já em uso** — muda `POSTGRES_PORT` no `.env` (ex. `5433`) e reaplica `docker compose up -d`.
- **App não liga à BD logo após `docker compose up`** — confirma `docker compose ps` até veres `healthy`; ver secção 6.
- **Erro do Hibernate tipo "Schema-validation: missing table"** — falta a migração Flyway correspondente à entidade; cria o `V*__*.sql` em `db/migration`.
- **Mudaste `POSTGRES_PASSWORD`/`POSTGRES_USER` no `.env` mas nada muda** — essas variáveis só são lidas pelo Postgres na **primeira** inicialização do volume. Se já existia dados no volume, faz `docker compose down -v` (apaga os dados!) e sobe de novo para reaplicar.
- **`docker compose up` reclama `variable is not set`** — falta essa variável no `.env` (secção 7). Não há defaults de credenciais no `docker-compose.yml`/`application.yml` de propósito (secção 7/8) — o `.env` é mesmo obrigatório.
- **`mvn spring-boot:run` não encontra a classe principal** — ver secção 10; corre pelo IntelliJ ou confirma que o caminho do projeto não tem caracteres especiais.
