# Guida didattica – Embedding Explorer 3D in classe

Questa guida è per il docente del corso «Full Stack Web Developer con Java e React». Spiega come
usare Embedding Explorer 3D nelle lezioni L16–L18 per far «vedere» agli studenti gli embedding
che il chatbot della L17 ha salvato in PostgreSQL con pgvector.

> **In una frase:** ogni punto del grafico è un chunk di un PDF; la sua posizione è l'«ombra» del
> suo embedding (1536 numeri) proiettata in 3D. Le distanze vere le calcola sempre il database.

---

## 1. Obiettivi della lezione

Alla fine dell'attività gli studenti sanno:

1. dire che cos'è un **chunk** e che cos'è il suo **embedding**: una lista di 1536 numeri, la
   «coordinata GPS del significato» (L18 §2.3);
2. spiegare perché testi simili finiscono vicini, e che **similarità = 1 − distanza del coseno**;
3. descrivere come la ricerca RAG sceglie i chunk: `topK` più vicini, poi la soglia
   `similarityThreshold` (nel `RagService` della L18: `topK = 5`, `similarityThreshold = 0.5`);
4. riconoscere che cosa si perde quando 1536 dimensioni vengono «schiacciate» in 3: il grafico è una
   proiezione, non la realtà.

## 2. Prerequisiti

- La **L17** è stata svolta: il chatbot ha indicizzato almeno un PDF e il database
  `pgvector/pgvector:pg17` (container `chatbot-postgres`) è avviato.
- In alternativa, il **database demo** dello strumento: 300 chunk **sintetici** (5 file finti, 4 temi
  più un file di FAQ che li mescola), utile per preparare la lezione o se il database della L17 non è
  disponibile. Ogni chunk demo inizia con «[DEMO – dati sintetici]».
- Docker Desktop (o Docker Engine su Linux) sul PC del docente.
- Facoltativo, per la simulazione RAG: una chiave OpenAI oppure Ollama con lo **stesso modello** usato
  per indicizzare la tabella (vedi §6).

## 3. Preparare l'aula (10 minuti prima)

1. Copiare `.env.example` in `.env` e controllare i valori del database (in aula valgono le
   credenziali della L17: `chatbot` / `chatbot123`).
2. Avviare lo strumento dalla cartella del progetto:
   - con il database della L17: `docker compose up -d --build`
   - con il database demo: `docker compose -f docker-compose.yml -f compose.demo.yaml up -d --build`
3. Aprire `http://localhost:8088`. Alla prima apertura compare una **guida iniziale** con le tre
   azioni principali; si chiude con «Ho capito» e si può rivedere da «Come leggere il grafico».
4. Nella sezione **Stato** tutti i controlli devono essere verdi (✓). Un avviso (!) non blocca la
   lezione; un errore (✕) mostra il messaggio e il suggerimento per risolverlo (vedi §7).
5. Per il proiettore: browser a schermo intero (F11) e il pulsante **«Modalità presentazione»**
   nell'intestazione (testo ed etichette 3D più grandi); se l'aula è grande, anche lo zoom del browser
   al 110–125%.

> Il dettaglio di installazione, configurazione e risoluzione dei problemi è nel `README.md` e in
> `docs/collegamento-database-vettoriale.md`.

## 4. Lettura guidata dell'interfaccia

La barra laterale a sinistra ha otto sezioni, che si aprono e chiudono cliccando il titolo. Ogni
sezione ha un pulsante **«i»** con una spiegazione di 2–4 frasi e la lezione di riferimento: è un buon
modo per far leggere agli studenti il «perché» di ogni pannello.

| Sezione | Che cosa mostra | Che cosa far notare |
| --- | --- | --- |
| **Stato** | I 10 controlli sul database (connessione, pgvector, tabella, colonne, righe, dimensioni, norme, indice, metadata, modello di embedding) e il riepilogo del dataset. | La tabella `vector_store` creata da Spring AI ha `id`, `content`, `metadata` (json) ed `embedding` (`vector(1536)`); l'indice HNSW con `vector_cosine_ops` dice che la metrica è il coseno. Le norme ≈ 1: i vettori OpenAI sono normalizzati. |
| **Come leggere il grafico** | Una guida di un minuto. | Il paragone dell'ombra sul muro (§5). |
| **Proiezione** | Metodo (PCA o UMAP), varianza spiegata di PC1, PC2, PC3 e totale; «Fedeltà dei vicini»; «Normalizza i vettori»; «Dimensione dei punti». | La nota fissa: «Le posizioni sono una proiezione: le distanze vere sono quelle calcolate dal database.» La fedeltà dice quanti vicini veri restano vicini nel grafico. |
| **Colori e filtri** | «Colora per» (file, pagina, indice del chunk, lunghezza del testo o qualunque chiave dei metadata), legenda, filtri per file e pagine, «Cerca nel testo», lista dei «Chunk visibili». | Clic su una voce della legenda la nasconde, Alt+clic la isola. La lista è l'alternativa accessibile al grafico: da qui si seleziona un chunk anche senza mouse. |
| **Chunk selezionato** | File, pagina, indice; testo completo; tabella dei metadata; statistiche del vettore; i primi 10 numeri; il «codice a barre» di tutti i numeri; i **vicini veri**. | Il codice a barre: ogni quadratino è un numero (griglia 48 × 32 per 1536), blu i negativi, rosso i positivi. Passando il mouse si legge il valore. |
| **Confronta due chunk** | Similarità del coseno, distanze e prodotto scalare **veri** accanto alla distanza **nel grafico**. | La frase «Il grafico mostra il X% della distanza vera». |
| **Simula una ricerca RAG** | Domanda, `topK`, `similarityThreshold`, risultati con «Entra nel contesto» / «Scartato dalla soglia», la query SQL di Spring AI. | È esattamente il passo di retrieval del `RagService` della L18. |
| **Esercizi** | Sei attività brevi (§8). | — |

### La vista 3D

- **Trascinare** con il tasto sinistro ruota, la **rotella** fa zoom, il **tasto destro** sposta.
- Passando sopra un punto compare un **tooltip** con file, pagina e inizio del testo.
- **Clic** su un punto lo seleziona: diventa più grande, con un contorno bianco, e linee gialle lo
  collegano ai suoi vicini veri; gli altri punti si **attenuano** (più piccoli e trasparenti).
  Clic su uno spazio vuoto deseleziona.
- **Doppio clic** avvicina la camera al punto. **«Reimposta vista»** torna all'inquadratura iniziale;
  **«Griglia»** mostra un piano di riferimento.
- **«Percorsi»** unisce i chunk di ogni file nell'ordine di lettura; **«Centroidi»** mostra il centro di
  ogni file con il suo nome. **«Salva immagine»** scarica un PNG della vista con la nota sulla
  proiezione, utile per le slide o per gli appunti degli studenti.
- Gli assi sono etichettati **PC1, PC2, PC3** con la percentuale di varianza che ciascuno conserva.
- In basso a sinistra c'è sempre il promemoria: «Proiezione PCA: le distanze nel grafico sono
  approssimate.»

## 5. Il messaggio di onestà (da ripetere più volte)

1. **Il grafico è un'ombra.** Un embedding ha 1536 dimensioni: non si può disegnare. La PCA cerca le tre
   direzioni in cui i chunk differiscono di più e proietta lì ogni punto, come l'ombra di un oggetto su
   un muro: la forma generale si riconosce, ma due punti lontani possono sembrare vicini.
2. **Quanto si perde si misura.** La sezione «Proiezione» dice quanta varianza resta visibile. Con gli
   embedding di testo veri si vede di solito ben meno della metà della varianza (con il database
   demo, più ordinato, circa il 42%): gran parte dell'informazione resta fuori dal grafico.
3. **I numeri veri vengono dal database.** Vicini, similarità e distanze sono calcolati da PostgreSQL
   con pgvector su tutti i 1536 numeri, mai sulle coordinate 3D.
4. **PCA o UMAP?** La PCA è un'ombra «onesta»: lineare, con la varianza spiegata. UMAP (sezione
   «Proiezione») tiene vicini i chunk che sono vicini davvero, ma deforma le distanze: due gruppi
   lontani nel grafico non sono per forza «più diversi» di due gruppi vicini. La **fedeltà dei vicini**
   misura quanto ci si può fidare di «vicino nel grafico» con l'uno e con l'altro metodo.
5. **Ricerca esatta contro ricerca approssimata.** La lista dei vicini veri confronta il vettore con
   *tutti* gli altri (ricerca esatta). Il chatbot invece usa l'indice **HNSW**, approssimato ma molto più
   veloce su milioni di righe; la simulazione RAG usa lo stesso indice, come Spring AI. Domanda per la
   classe: «Perché in produzione si accetta un risultato approssimato?»

## 6. La simulazione RAG: come attivarla

Di default la sezione «Simula una ricerca RAG» è **disattivata** e spiega che cosa configurare: serve
un modello di embedding per trasformare la domanda in vettore.

- **OpenAI** (lo stesso modello della L17): nel file `.env`
  `VIZ_EMBEDDING_ENABLED=true`, `VIZ_EMBEDDING_MODEL=text-embedding-3-small`,
  `VIZ_EMBEDDING_API_KEY=…`. Ogni ricerca costa una frazione di centesimo.
- **Ollama** (gratuito, in locale): `VIZ_EMBEDDING_BASE_URL=http://host.docker.internal:11434/v1` e lo
  stesso modello usato per indicizzare la tabella (per esempio `bge-m3` per `vector_store_bge_m3`,
  L18 §12).
- Dopo la modifica: `docker compose up -d`. Nella sezione **Stato**, la riga «Modello di embedding»
  diventa verde e il pulsante **«Prova il modello»** fa una vera chiamata di prova (mostra modello,
  dimensioni e tempo di risposta).

Regole da ricordare in classe:

- La domanda va trasformata con **lo stesso modello** usato per indicizzare la tabella (L18 §12.1). Se le
  dimensioni non coincidono lo strumento lo dice (`DIMENSION_MISMATCH`).
- Un limite di ricerche al minuto (`VIZ_SEARCH_RATE_LIMIT_PER_MINUTE`, 10 di default) protegge il
  servizio a pagamento: se la classe lo supera compare «Troppe ricerche in poco tempo»; basta aspettare
  qualche secondo. Le ricerche non vengono mai ripetute automaticamente.

## 7. Problemi frequenti in aula

| Che cosa si vede | Causa probabile | Soluzione |
| --- | --- | --- |
| «Il database vettoriale non risponde.» (`DB_UNAVAILABLE`) | Il database della L17 è spento. | `docker compose up -d` nella cartella `docker` del progetto L17. Lo strumento riprova da solo ogni 10 secondi e torna verde senza ricaricare la pagina. |
| «Il database ha rifiutato utente o password.» (`DB_AUTH_FAILED`) | Password sbagliata nel `.env`. | Correggere `VIZ_DB_PASSWORD` e riavviare: `docker compose up -d`. |
| «La tabella dei vettori non esiste.» (`TABLE_NOT_FOUND`) | Il chatbot non è mai stato avviato (è lui a creare `vector_store`), oppure tabella o schema sbagliati. | Avviare il chatbot una volta, o controllare `VIZ_DB_TABLE` e `VIZ_DB_SCHEMA`. |
| «Nel database manca l'estensione pgvector.» (`PGVECTOR_MISSING`) | Il database non usa l'immagine `pgvector/pgvector:pg17`. | Usare il compose della L17. |
| «La tabella è vuota.» | Nessun PDF indicizzato. | Caricare un PDF nel chatbot. |
| Avviso «Il grafico mostra un campione di N chunk su M» | La tabella ha più chunk di `VIZ_MAX_POINTS` (5000). | Normale: il campione è sempre lo stesso. Per vederli tutti alzare `VIZ_MAX_POINTS` (attenzione alla memoria). |
| La vista 3D non compare | Browser senza WebGL 2 o accelerazione grafica disattivata. | Usare un browser recente; nel frattempo la lista «Chunk visibili» permette di lavorare lo stesso. |

Ogni messaggio di errore mostra anche un **codice richiesta**: se un problema si ripete, quel codice
permette di trovare la riga giusta nei log (`docker compose logs viz-backend`).

## 8. Esercizi

Ogni esercizio ha un obiettivo, i passi, che cosa ci si aspetta di vedere e alcune domande per la
discussione. Si possono svolgere in coppia, uno studente ai comandi e uno che prende appunti.

### Esercizio A – Vicini nel grafico, lontani nella realtà

- **Obiettivo:** capire che la proiezione può ingannare.
- **Passi:**
  1. Ruotare il grafico e trovare due punti **molto vicini** ma di file (colori) diversi.
  2. Selezionare il primo e premere «Usa come A»; selezionare il secondo e premere «Usa come B».
  3. In «Confronta due chunk» leggere la similarità del coseno vera e la distanza nel grafico.
- **Che cosa ci si aspetta:** spesso la similarità vera è bassa (per esempio 0,2) anche se i punti
  sembrano attaccati. La riga «Il grafico mostra il X% della distanza vera» è sempre al massimo 100%:
  una proiezione può solo **accorciare** le distanze, mai allungarle.
- **Discussione:** perché due chunk possono avere la stessa «ombra»? (Differiscono lungo direzioni che
  il grafico non mostra.) Quale numero userebbe il chatbot per decidere? (La similarità vera.)
- **Suggerimento per il docente:** con il database demo i chunk delle FAQ (arancioni) stanno dentro i
  cluster degli altri file: confrontarli con il file del loro tema dà similarità alte, con gli altri basse.

### Esercizio B – Dove finisce un nuovo PDF?

- **Obiettivo:** collegare l'indicizzazione della L17 ai punti del grafico.
- **Passi:**
  1. Lasciare aperto lo strumento con «Colora per: File».
  2. Caricare un nuovo PDF nel chatbot della L17.
  3. Entro circa 30 secondi compare l'avviso «I dati nel database sono cambiati… ricarico i punti»;
     poi «Punti aggiornati con i nuovi dati».
  4. Nella legenda, Alt+clic sul nuovo file per isolarlo; attivare «Percorsi» e «Centroidi» per vedere
     come il documento «cammina» da una pagina all'altra e dove sta il suo centro.
- **Che cosa ci si aspetta:** i nuovi chunk si dispongono vicino ai file che trattano argomenti simili;
  se il PDF parla di un tema nuovo, formano un gruppo a sé.
- **Discussione:** perché nessuno ha dovuto «spostare» i vecchi punti? (Ogni embedding dipende solo dal
  suo testo; la PCA però viene ricalcolata, quindi l'inquadratura può cambiare un po'.)

### Esercizio C – topK e soglia

- **Obiettivo:** vedere che cosa entra davvero nel contesto del modello (L18 §2.4 e §5).
- **Passi:**
  1. In «Simula una ricerca RAG» scrivere una domanda sul contenuto dei PDF.
  2. Cercare con `topK` 3 e poi 8 (soglia 0,5).
  3. Ripetere con `topK` 8 e soglia 0,3, poi 0,7.
- **Che cosa ci si aspetta:** i risultati sono in ordine di similarità; ognuno ha il badge «Entra nel
  contesto» o «Scartato dalla soglia». Nel grafico la **domanda** è un ottaedro bianco, collegato ai
  chunk con linee **verdi** (entrano) o **arancioni tratteggiate** (scartati).
- **Discussione:** che cosa succede al prompt se la soglia è troppo alta? (Nessun chunk entra: il modello
  risponde senza contesto.) E se è troppo bassa? (Entrano chunk poco pertinenti e consumano token.)
  Leggere la query SQL che Spring AI esegue: il terzo parametro è **1 − soglia**, cioè la distanza
  massima accettata.

### Esercizio D – Quanto perde la proiezione?

- **Obiettivo:** dare un numero all'«ombra».
- **Passi:** aprire «Proiezione» e leggere la varianza di PC1, PC2, PC3 e il totale; leggere la
  «Fedeltà dei vicini»; poi scegliere UMAP e rileggere la fedeltà.
- **Che cosa ci si aspetta:** un totale di solito ben sotto il 50% (circa il 42% con il database
  demo); la frase «il X% non si vede». Con il database demo la PCA conserva in media meno di 2 dei 10
  vicini veri, UMAP quasi 5: UMAP separa meglio i gruppi, ma non ha una varianza spiegata e le sue
  distanze non si confrontano con quelle vere.
- **Discussione:** se la varianza è poca, ha senso fidarsi delle distanze nel grafico? Perché i vicini
  veri (sezione «Chunk selezionato») a volte non sono i punti più vicini nel 3D? Perché la domanda
  della ricerca RAG, con UMAP, ha una «posizione approssimata»?

### Esercizio E – Il metadata `department`

- **Obiettivo:** collegare i metadata ai filtri della ricerca (L18 §7).
- **Passi:** «Colora per → department»; poi filtrare per file e confrontare.
- **Che cosa ci si aspetta:** con il database demo i reparti (HR, Sicurezza, Amministrazione, IT)
  coincidono quasi con i cluster; le FAQ li mescolano.
- **Discussione:** un filtro sui metadata (per esempio `department == 'HR'`) restringe i candidati
  *prima* della ricerca per similarità: quando è utile? Che cosa succede se un documento ha il reparto
  sbagliato nei metadata?

### Esercizio F – Due modelli, due mappe

- **Obiettivo:** capire che ogni modello di embedding crea il suo «spazio» (L18 §12).
- **Passi:**
  1. Nel `.env` puntare a un'altra tabella, per esempio `VIZ_DB_TABLE=vector_store_bge_m3` (Ollama
     `bge-m3`, 1024 dimensioni) o `vector_store_minilm` (`all-MiniLM-L6-v2`, 384 dimensioni); con il
     database demo è già pronta `vector_store_minilm`.
  2. `docker compose up -d` e confrontare: dimensioni (sezione «Stato»), forma della nuvola, vicini veri
     dello stesso testo.
- **Discussione:** perché non si possono mettere vettori di modelli diversi nella stessa tabella?
  (Hanno dimensioni diverse e, anche a parità di dimensioni, significati diversi delle coordinate.)

## 9. Scaletta suggerita (60–90 minuti)

| Tempo | Attività |
| --- | --- |
| 0–10' | Ripasso: chunk, embedding, pgvector (L17). Sezione «Stato» al proiettore. |
| 10–20' | «Come leggere il grafico» e la metafora dell'ombra; sezione «Proiezione» (esercizio D). |
| 20–35' | Selezione di un chunk: testo, metadata, codice a barre, vicini veri. |
| 35–50' | Esercizio A in coppia, poi discussione. |
| 50–65' | Simulazione RAG ed esercizio C (se disattivata: mostrare la query SQL e i parametri sui dati della lezione L18). |
| 65–80' | Esercizio B dal vivo (nuovo PDF) oppure F (due modelli). |
| 80–90' | Riepilogo: che cosa si vede, che cosa no, e perché il database è l'unico giudice delle distanze. |

## 10. Riepilogo per gli studenti

- Un **chunk** è un pezzo di PDF; il suo **embedding** è una lista di numeri che ne rappresenta il
  significato.
- Testi simili hanno embedding vicini: la **similarità** è 1 − distanza del coseno.
- Il RAG prende i `topK` chunk più simili alla domanda e tiene solo quelli sopra la soglia.
- Il grafico 3D è una **proiezione**: utile per orientarsi, ma le distanze vere sono quelle calcolate dal
  database su tutte le dimensioni.
