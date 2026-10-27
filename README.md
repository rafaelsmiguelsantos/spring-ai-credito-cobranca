# credito-ai

Projeto de apoio a dois artigos da newsletter *Chronicles of a Pragmatic Programmer*
sobre Spring AI aplicado a crédito e cobrança.

- **Artigo 1 — fundamentos.** `ChatClient`, saída estruturada, tool calling, advisors,
  memória, observability e como se testa isso. Pacotes `atendimento`, `divida`, `advisor`.
- **Artigo 2 — RAG sobre política de cobrança.** Pipeline ETL, chunking por seção,
  filtro por metadado, citação de fonte e validação do número contra o domínio.
  Pacote `politica`.

> O manual em `src/main/resources/politica/` é **fictício**. Não reproduz a política
> de nenhuma instituição real.

## A tese do projeto

Um modelo de linguagem não produz nenhum valor monetário aqui, e não decide nada.

| O que o modelo faz | O que o código faz |
|---|---|
| Lê a transcrição e classifica | Define o vocabulário fechado (`enum`) |
| Pede uma ferramenta | `CalculadoraAcordo` faz a conta |
| Propõe um desconto | `AlcadaOficial` diz qual é o teto e quem aprova |
| Responde com base no manual | `ConsultaAlcadaService` confere o número e a citação |

A última linha é a que costuma faltar em demonstrações de RAG. Recuperação reduz
alucinação; não elimina. O modelo pode puxar a seção certa e ler a linha errada
dela, e "até 45%" contra "até 65%" num saldo de cinco mil reais é prejuízo que
nenhuma fluência denuncia.

## Stack

| | |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.2 |
| Spring AI | 2.0.2 |
| Vector store | pgvector |
| Modelos | Ollama (padrão) ou OpenAI |

## Pré-requisitos

- **JDK 21.** O `pom.xml` fixa `java.version` em 21; com JDK 17 o build falha na
  compilação, não em tempo de execução.
- **Docker**, para o pgvector e para os testes de integração (Testcontainers).
- **Ollama**, para rodar sem gastar nada. Alternativa: perfil `openai`.
- **Maven 3.9+.** O repositório não tem wrapper. Se quiser um:
  `mvn wrapper:wrapper` gera `mvnw` e `mvnw.cmd`.

## Rodando

### 1. Banco

```bash
docker compose up -d
```

### 2. Modelos

Com Ollama local (nada a pagar):

```bash
ollama pull qwen2.5:7b
ollama pull nomic-embed-text
```

Com OpenAI:

```bash
export OPENAI_API_KEY=sk-...
```

### 3. Aplicação

```bash
# Ollama e o perfil padrao: as duas linhas fazem a mesma coisa
mvn spring-boot:run
mvn spring-boot:run -Dspring-boot.run.profiles=ollama

# OpenAI - mesmo codigo, outro provider
mvn spring-boot:run -Dspring-boot.run.profiles=openai
```

Trocar de provider é trocar o profile. Nenhuma classe Java muda. É a tese central
do Spring AI, e vale conferir em vez de acreditar.

> As dimensões do vetor mudam com o modelo de embedding (768 no `nomic-embed-text`,
> 1536 no `text-embedding-3-small`), e a coluna do pgvector é criada com a dimensão
> fixa. Por isso cada perfil usa a **sua própria tabela** (`vector_store_ollama_768`
> e `vector_store_openai_1536`): os dois convivem no mesmo banco e alternar perfis
> não exige `docker compose down -v`.

## Endpoints

```bash
# Artigo 1 - classificacao de atendimento
curl -X POST localhost:8080/atendimentos/classificacao \
  -H 'Content-Type: application/json' \
  -d '{"transcricao":"Cliente: perdi o emprego em agosto. Dia 10 eu pago 800 reais."}'

# Artigo 1 - assistente multi-turno, com ferramentas e memoria.
# O mesmo conversationId continua a conversa.
curl -X POST localhost:8080/assistente/analista-42 \
  -H 'Content-Type: application/json' \
  -d '{"pergunta":"Qual a posicao do contrato CT-00231907?"}'

curl -X POST localhost:8080/assistente/analista-42 \
  -H 'Content-Type: application/json' \
  -d '{"pergunta":"E com 20% de desconto em 4 parcelas?"}'

# Artigo 2 - ingestao do manual (idempotente: pode repetir)
curl -X POST localhost:8080/politica/ingestao

# Artigo 2 - consulta com verificacao de alcada
curl -G localhost:8080/politica/consulta \
  --data-urlencode 'pergunta=Qual o desconto maximo a vista?' \
  --data-urlencode 'carteira=CARTAO' \
  --data-urlencode 'diasEmAtraso=180'
```

Vale olhar dois campos na resposta da consulta: `divergente`, quando o número do
modelo não bate com a tabela, e `semCitacao`, quando o modelo respondeu sem apontar
a seção. Os dois são `false` numa resposta saudável, e `confiavel` resume isso.

Na simulação de acordo, o retorno não tem apenas os valores: `percentualMaximoPolitica`,
`aprovacao` e `restricoes` dizem se o que foi pedido cabe na alçada do operador,
precisa de supervisor ou precisa do gerente de recuperação.

## Configuração

Fora dos perfis de provider, o que se costuma ajustar:

| Propriedade | Padrão | Para quê |
|---|---|---|
| `credito.politica.top-k` | 4 | Quantas seções recuperar |
| `credito.politica.similarity-threshold` | 0.45 | Similaridade mínima do chunk |
| `credito.politica.manual` | `classpath:politica/manual-cobranca-v3.md` | Onde está o manual |
| `spring.ai.tools.limits.max-total-tool-calls` | 10 | Teto do loop de ferramentas |

As duas observações de prompt (`spring.ai.chat.client.observations.log-prompt` e
`log-completion`) ficam explicitamente em `false`. Neste domínio o prompt carrega
uma transcrição e a resposta carrega uma avaliação da situação financeira de uma
pessoa com nome; ligar isso manda tudo para o backend de observabilidade sob uma
flag que parece de debug. O `AuditTrailAdvisor` registra modelo, latência, tamanhos
e contagem de tokens, que é o que uma auditoria pede.

## Testes

```bash
mvn test                 # unitarios, sem IA, sem Docker: rodam em segundos
mvn verify               # inclui os *IT, que exigem Ollama e Docker
```

A divisão é intencional e o Failsafe está declarado no `pom.xml` para que ela
exista de verdade. `mvn test` cobre tudo que é determinístico — faixas de atraso,
tabela de alçada, aritmética do acordo, chunking do manual, detecção de divergência
e validação dos endpoints — e não depende de nada externo. Os `*IT` sobem contexto,
pgvector via Testcontainers e falam com um modelo real.

Vale notar o que os testes determinísticos testam: não é "o modelo acerta", é
"quando o modelo erra, o código percebe". Essa é uma pergunta que precisa de uma
resposta errada de propósito, e por isso `ConsultaAlcadaServiceTest` usa um dublê
em vez de um modelo.
