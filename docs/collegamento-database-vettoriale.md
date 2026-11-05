# Collegare lo strumento al database vettoriale

Questa guida spiega, passo per passo, come collegare Embedding Explorer 3D al database della L17,
come controllare i dati, come usare un utente di sola lettura e come risolvere i problemi più comuni.
Lo strumento **legge soltanto**: non modifica mai tabelle, schema o dati della L17.

## I valori della L17 in breve

| Voce | Valore nella L17 |
| --- | --- |
| Docker | immagine `pgvector/pgvector:pg17`, servizio `postgres`, container `chatbot-postgres`; il compose è in `docker/docker-compose.yml` del progetto L17 |
| Host e porta | `localhost:5432` (pubblicata sull'host) |
| Database / utente / password | `chatbot` / `chatbot` / `chatbot123` (credenziali di classe) |
| Estensioni (`init.sql`) | `vector`, `hstore`, `uuid-ossp` |
| Tabella | `public.vector_store`, creata da Spring AI `PgVectorStore` |
| Colonne | `id uuid`, `content text`, `metadata json`, `embedding vector(1536)` |
| Indice | HNSW su `embedding` con `vector_cosine_ops`, nome `spring_ai_vector_index` |
| Modello | OpenAI `text-embedding-3-small`, 1536 dimensioni, vettori di lunghezza 1 |

Ognuno di questi valori si può cambiare con le variabili `VIZ_*` del file `.env` (vedi il
[README](../README.md#configurazione)).

## 1. Scegli come collegarti

Il backend gira in un container Docker, quindi per lui `localhost` è il container stesso, non il tuo
computer. Ci sono quattro modi per raggiungere il database.

### Opzione A (predefinita): la porta pubblicata sull'host

Il compose della L17 pubblica la porta 5432 sul tuo computer. Il backend la raggiunge con il nome
speciale `host.docker.internal`:

```bash
VIZ_DB_URL=jdbc:postgresql://host.docker.internal:5432/chatbot
```

Il nostro `docker-compose.yml` aggiunge al backend:

```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```

Su Linux questa riga è **necessaria** (serve Docker Engine 20.10 o più recente); su Docker Desktop
(Windows e macOS) è innocua. Per verificare che la L17 pubblichi la porta:

```bash
docker ps --filter name=chatbot-postgres
```

Nella colonna `PORTS` devi vedere qualcosa come `0.0.0.0:5432->5432/tcp`.

### Opzione B: entrare nella rete Docker della L17

Il backend si unisce alla rete del compose della L17 e raggiunge il database con il nome del
servizio, senza passare dall'host. Utile su Linux con Docker vecchio o con un firewall restrittivo.

1. Avvia il database della L17 (la rete deve esistere prima).
2. Trova il nome della rete:

   ```bash
   docker network ls
   ```

   Con la struttura della L17 di solito è `docker_default` (nome della cartella `docker` + `_default`).
   Se è diverso, scrivilo nel file `.env`: `VIZ_L17_NETWORK=nome_della_rete`.

3. Avvia lo strumento con il file aggiuntivo per la rete:

   ```bash
   docker compose -f docker-compose.yml -f compose.l17-network.yaml up --build -d
   ```

Questo file collega il backend alla rete esterna `${VIZ_L17_NETWORK:-docker_default}` e imposta
`VIZ_DB_URL=jdbc:postgresql://postgres:5432/chatbot` (va bene anche il nome del container,
`chatbot-postgres`). Se compose risponde `network docker_default declared as external, but could not
be found`, il database della L17 non è avviato o la rete ha un altro nome.

### Opzione C: backend avviato dall'IDE

Se avvii il backend da IntelliJ, VS Code o dal terminale, gira sul tuo computer e `localhost` funziona:

```bash
cd backend
VIZ_DB_URL=jdbc:postgresql://localhost:5432/chatbot VIZ_DB_PASSWORD=chatbot123 ./mvnw spring-boot:run
```

Nell'IDE imposta le stesse variabili nella configurazione di avvio. Poi avvia il frontend con
`npm ci` e `npm run dev` nella cartella `frontend` e apri <http://localhost:5173>.

### Opzione D: database demo (senza la L17)

Per provare lo strumento, o per sviluppare, c'è un database demo con dati **sintetici**:

```bash
docker compose -f docker-compose.yml -f compose.demo.yaml up --build -d
```

Contiene `public.vector_store` (300 chunk sintetici, 1536 dimensioni, 5 file) e
`public.vector_store_minilm` (gli stessi chunk a 384 dimensioni). Il backend viene collegato da solo
al database demo; per cambiare tabella imposta `VIZ_DB_TABLE=vector_store_minilm`. Ogni chunk dice
di essere sintetico nel testo e ha `"demo": true` nei metadata.

## 2. Controlla i dati con psql o pgAdmin

Prima di dare la colpa allo strumento, guarda i dati direttamente. Con psql dentro il container
della L17:

```bash
docker exec -it chatbot-postgres psql -U chatbot -d chatbot
```

Con pgAdmin crea una connessione con host `localhost`, porta `5432`, database `chatbot`, utente
`chatbot`, password `chatbot123`.

Poi prova queste query, una alla volta:

```sql
-- È installato pgvector? E in che versione?
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';

-- Quanti chunk ci sono?
SELECT count(*) FROM public.vector_store;

-- Quante dimensioni hanno i vettori? (con la L17: una sola riga, 1536)
SELECT vector_dims(embedding) AS dims, count(*) FROM public.vector_store GROUP BY 1;

-- Quali chiavi ci sono nei metadata?
SELECT DISTINCT json_object_keys(metadata) FROM public.vector_store WHERE json_typeof(metadata) = 'object';

-- Quanti chunk per ogni file caricato?
SELECT metadata->>'original_filename' AS file, count(*) FROM public.vector_store GROUP BY 1 ORDER BY 2 DESC;

-- I numeri veri di un vettore
SELECT embedding::real[] FROM public.vector_store LIMIT 1;
```

Se queste query funzionano, il database è a posto: il problema è nel collegamento (sezione 1) o
nelle credenziali (sezione 6).

## 3. (Consigliato) Crea un utente di sola lettura

Lo strumento apre già la connessione in sola lettura, ma un utente dedicato rende **impossibile**
qualunque scrittura, anche per errore. Esegui queste istruzioni con psql come utente `chatbot`
(il proprietario delle tabelle), scegliendo una password vera al posto di `change-me`:

```sql
CREATE ROLE viz_reader LOGIN PASSWORD 'change-me';
GRANT CONNECT ON DATABASE chatbot TO viz_reader;
GRANT USAGE ON SCHEMA public TO viz_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO viz_reader;
-- da eseguire come proprietario delle tabelle (chatbot), per le tabelle create in futuro (L18 §12)
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO viz_reader;
```

Poi, nel file `.env`:

```bash
VIZ_DB_USER=viz_reader
VIZ_DB_PASSWORD=la-password-scelta
```

`GRANT SELECT ON ALL TABLES` vale solo per le tabelle che esistono **adesso**. L'ultima istruzione,
`ALTER DEFAULT PRIVILEGES`, dà il permesso anche alle tabelle create **dopo** (per esempio quelle
della L18 §12), ma solo se le crea l'utente che la esegue: per questo va lanciata come `chatbot`, lo
stesso utente con cui il chatbot crea le tabelle. Se compare `DB_PERMISSION_DENIED` su una tabella
nuova, ripeti il `GRANT SELECT ON ALL TABLES`.

Per verificare: collegati come `viz_reader`; `SELECT count(*) FROM public.vector_store;` deve
funzionare, mentre un `DELETE` deve fallire con `permission denied`.

## 4. Tabelle della L18 e altre metriche

**Altre tabelle.** Nella L18 §12 ogni modello di embedding ha la sua tabella. Per esplorarne una basta
cambiare il nome (e lo schema, se serve):

```bash
VIZ_DB_TABLE=vector_store_bge_m3
```

oppure `VIZ_DB_TABLE=vector_store_minilm`. Le dimensioni (1024 per `bge-m3`, 384 per
`all-MiniLM-L6-v2`) vengono lette dai dati: non c'è niente da configurare. Se in una tabella ci sono
vettori di dimensioni diverse lo strumento non li mescola e mostra `DIMENSION_MISMATCH`.

**Altre metriche.** Con `VIZ_DISTANCE_METRIC=auto` (il default) la metrica viene letta dall'operator
class dell'indice sulla colonna `embedding`, cioè dalla `distance-type` scelta per Spring AI:

| Operator class dell'indice | Metrica | Operatore SQL |
| --- | --- | --- |
| `vector_cosine_ops` | coseno (`COSINE_DISTANCE`) | `<=>` |
| `vector_l2_ops` | euclidea (`EUCLIDEAN_DISTANCE`) | `<->` |
| `vector_ip_ops` | prodotto interno (`NEGATIVE_INNER_PRODUCT`) | `<#>` |

Senza indice si usa il coseno, come Spring AI. Puoi forzare una metrica con
`VIZ_DISTANCE_METRIC=cosine`, `l2` o `inner_product`; se è diversa da quella dell'indice, la sezione
**Stato** te lo segnala (l'indice non potrebbe aiutare le ricerche). L'interruttore "Normalizza i
vettori" ha senso, e funziona, solo con il coseno.

**La simulazione RAG su un'altra tabella** richiede lo **stesso modello** con cui la tabella è stata
indicizzata (L18 §12.1). Per `vector_store_bge_m3`, per esempio, Ollama con `VIZ_EMBEDDING_MODEL=bge-m3`
(1024 dimensioni). Con un modello diverso le dimensioni non tornano e lo strumento risponde
`DIMENSION_MISMATCH`; con lo stesso numero di dimensioni ma un modello diverso i risultati sarebbero
senza senso, ed è per questo che la regola conta.

## 5. La checklist "Stato"

La sezione **Stato** esegue questi controlli, in ordine. Se uno fallisce, quelli che dipendono da lui
vengono **saltati**, così vedi subito la causa vera:

1. **Connessione al database**: il server risponde? Che versione è?
2. **Estensione pgvector**: è installata?
3. **Tabella dei vettori**: esiste ed è leggibile dall'utente configurato?
4. **Colonne della tabella**: `id`, `content`, `metadata` (json) ed `embedding` (vector)?
5. **Righe (chunk)**: avviso se la tabella è vuota o se ci sono righe senza embedding.
6. **Dimensioni dei vettori**: avviso se sono mescolate.
7. **Lunghezza dei vettori (norma)**: informativo, i vettori di OpenAI sono lunghi circa 1.
8. **Indice e metrica**: tipo e operator class dell'indice, e la metrica in uso.
9. **Chiavi dei metadata**: avviso se manca una chiave con il nome del file.
10. **Modello di embedding (ricerca RAG)**: solo la configurazione; **Prova il modello** fa una
    chiamata vera.

Finché qualcosa fallisce, la checklist si ripete da sola **ogni 10 secondi**. Quando il database
torna disponibile lo strumento se ne accorge e ricarica i dati da solo, senza riavvii.

## 6. Risoluzione dei problemi

Ogni errore mostrato dallo strumento ha un codice (per esempio `DB_UNAVAILABLE`) e un codice
richiesta: con quello puoi trovare la riga esatta nei log con `docker compose logs viz-backend`.

| Errore | Causa | Soluzione |
| --- | --- | --- |
| `DB_UNAVAILABLE` – "Il database vettoriale non risponde." (*Connection refused*) | il database della L17 è spento, oppure host o porta in `VIZ_DB_URL` sono sbagliati | avvia il database (`docker compose up -d` nella cartella `docker` della L17), poi controlla `VIZ_DB_URL` |
| `DB_UNAVAILABLE` con *UnknownHostException: host.docker.internal* | Linux con Docker più vecchio di 20.10, che non conosce `host-gateway` | aggiorna Docker oppure usa l'opzione B |
| `DB_UNAVAILABLE` anche se il database è acceso | la porta 5432 non è pubblicata, è pubblicata solo su `127.0.0.1` dell'host, o un firewall blocca il traffico dai container | controlla la colonna `PORTS` di `docker ps`, oppure usa l'opzione B |
| `DB_UNAVAILABLE` dopo circa 5 secondi di attesa | l'host del database non risponde affatto (indirizzo sbagliato, rete o firewall) | verifica l'indirizzo; il tempo di attesa è `VIZ_DB_CONNECTION_TIMEOUT` |
| `DB_AUTH_FAILED` – "Il database ha rifiutato utente o password." (*password authentication failed for user*) | `VIZ_DB_USER` o `VIZ_DB_PASSWORD` sbagliati, o password vuota | correggi il file `.env` (nella L17: `chatbot` / `chatbot123`) e riavvia con `docker compose up -d` |
| `DB_PERMISSION_DENIED` – "L'utente del database non può leggere la tabella." (*permission denied for table*) | l'utente di sola lettura non ha il permesso, magari su una tabella creata dopo | come `chatbot`: `GRANT SELECT ON ALL TABLES IN SCHEMA public TO viz_reader;` (sezione 3) |
| `DB_NOT_FOUND` – "Il database indicato non esiste." (*database "x" does not exist*) | nome del database sbagliato alla fine di `VIZ_DB_URL` | nella L17 il database si chiama `chatbot` |
| `PGVECTOR_MISSING` – "Nel database manca l'estensione pgvector." (*type "vector" does not exist*, *operator does not exist*) | il database usa l'immagine `postgres` semplice, o l'estensione non è stata creata | usa l'immagine `pgvector/pgvector:pg17`; come `chatbot` esegui `CREATE EXTENSION vector;` (lo fa l'`init.sql` della L17) |
| `TABLE_NOT_FOUND` – "La tabella dei vettori non esiste." (*relation does not exist*, *schema does not exist*) | il chatbot non ha mai creato la tabella, oppure `VIZ_DB_SCHEMA` o `VIZ_DB_TABLE` sono sbagliati | avvia il chatbot almeno una volta (crea `vector_store`) e controlla i nomi |
| `TABLE_SCHEMA_INVALID` – "La tabella non ha la struttura creata da Spring AI." | la tabella non è di `PgVectorStore`: mancano colonne o hanno un tipo diverso | servono `id`, `content`, `metadata` (json o jsonb) ed `embedding` (vector); la checklist dice cosa manca |
| `DB_TIMEOUT` – "Il database ha impiegato troppo tempo." | una query ha superato il timeout (tabella molto grande, disco lento) | riprova; riduci `VIZ_MAX_POINTS` o aumenta `VIZ_DB_STATEMENT_TIMEOUT` |
| `DIMENSION_MISMATCH` – "Le dimensioni dei vettori non coincidono." | la tabella mescola modelli diversi, oppure la domanda della ricerca RAG è trasformata con un altro modello | una tabella per modello (L18 §12); `VIZ_EMBEDDING_MODEL` deve essere il modello della tabella |
| `SNAPSHOT_TOO_LARGE` – "Troppi vettori da caricare in memoria." | righe × dimensioni superano il budget di memoria | riduci `VIZ_MAX_POINTS` o aumenta `VIZ_SNAPSHOT_MEMORY_BUDGET_MB` |
| `BACKEND_UNAVAILABLE` – "Il backend non risponde." | il container `viz-backend` si sta avviando o si è fermato | aspetta qualche secondo; poi `docker compose ps` e `docker compose logs viz-backend` |
| Il container `viz-backend` si riavvia di continuo | una variabile del file `.env` non è valida (per esempio una tabella con lettere maiuscole) | il log indica la variabile e il motivo: correggila e riavvia |

Un database spento non blocca mai lo strumento: la pagina si apre comunque, la sezione **Stato**
mostra `DB_UNAVAILABLE` con il suo suggerimento e tutto riparte da solo quando il database torna.

## Comandi da non usare mai sul progetto della L17

Questi comandi cancellano i dati della L17 (dovresti reindicizzare tutti i PDF, pagando di nuovo gli
embedding):

- `docker compose down -v`
- `docker volume rm …`
- `docker system prune`

Per fermare il database della L17 basta `docker compose stop` (o `docker compose down`, **senza** `-v`).
