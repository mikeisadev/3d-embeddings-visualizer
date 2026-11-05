/**
 * Every text the students see, in Italian, in one place. Components never hard-code
 * user-facing strings: they read them from here.
 */
import { formatInteger, formatNumber, formatPercent } from '../lib/format';

/** An Italian message with an actionable hint. */
export interface MessageWithHint {
  readonly message: string;
  readonly hint: string;
}

export const it = {
  app: {
    name: 'Embedding Explorer 3D',
    tagline: 'Gli embedding pgvector della L17, in 3D',
    sidebarLabel: 'Pannello informazioni e strumenti',
    sceneLabel: 'Vista 3D dei chunk',
  },

  header: {
    loadingDataset: 'Caricamento del dataset…',
    datasetUnavailable: 'Dataset non disponibile',
    dataset: (table: string, chunks: number, dimensions: number | null) =>
      `${table} · ${formatInteger(chunks)} chunk` +
      (dimensions === null ? '' : ` · ${formatInteger(dimensions)} dimensioni`),
    presentationOn: 'Modalità presentazione',
    presentationOff: 'Esci dalla presentazione',
  },

  common: {
    loading: 'Caricamento…',
    retry: 'Riprova',
    close: 'Chiudi',
    showMore: (count: number) => `Mostra altri ${formatInteger(count)}`,
    info: (section: string) => `Informazioni: ${section}`,
    lesson: 'Lezione',
    requestId: 'Codice richiesta',
    errorCode: 'Codice errore',
    technicalDetail: 'Dettaglio tecnico',
    none: 'nessuno',
    yes: 'sì',
    no: 'no',
    expand: (section: string) => `Apri la sezione ${section}`,
    collapse: (section: string) => `Chiudi la sezione ${section}`,
  },

  notices: {
    datasetChanged:
      'I dati nel database sono cambiati (per esempio è stato caricato un nuovo PDF): ricarico i punti.',
    datasetReloaded: 'Punti aggiornati con i nuovi dati.',
    dismiss: 'Chiudi l’avviso',
  },

  status: {
    title: 'Stato',
    info: 'Questa lista verifica, passo per passo, che il database della L17 sia raggiungibile e che la tabella dei vettori abbia la forma creata da Spring AI. Se un controllo fallisce, quelli che dipendono da lui vengono saltati. Il controllo si ripete da solo ogni 10 secondi finché il database non risponde.',
    lesson: 'L17 (PgVectorStore e database vettoriale)',
    checklist: 'Controlli',
    overall: { OK: 'Tutto a posto', WARN: 'Funziona, con avvisi', FAIL: 'C’è un problema' },
    statusLabel: { OK: 'OK', WARN: 'Avviso', FAIL: 'Errore', SKIPPED: 'Saltato' },
    checks: {
      'db.connection': 'Connessione al database',
      'db.pgvector': 'Estensione pgvector',
      'table.exists': 'Tabella dei vettori',
      'table.columns': 'Colonne della tabella',
      'table.rows': 'Righe (chunk)',
      'embedding.dimensions': 'Dimensioni dei vettori',
      'embedding.norms': 'Lunghezza dei vettori (norma)',
      'table.index': 'Indice e metrica',
      'metadata.keys': 'Chiavi dei metadata',
      'embedding.client': 'Modello di embedding (ricerca RAG)',
    } as Record<string, string>,
    skippedHint: 'Saltato perché un controllo precedente non è passato.',
    summaryTitle: 'Dataset',
    chunks: 'Chunk',
    files: 'File',
    dimensions: 'Dimensioni',
    metric: 'Metrica',
    index: 'Indice',
    noIndex: 'nessun indice',
    sampled: (loaded: number, total: number) =>
      `Il grafico mostra un campione di ${formatInteger(loaded)} chunk su ${formatInteger(total)} (limite VIZ_MAX_POINTS). Il campione è sempre lo stesso a ogni caricamento.`,
    metricSource: {
      DETECTED: 'rilevata dall’indice',
      CONFIGURED: 'configurata',
      DEFAULT: 'predefinita',
    },
    metricName: { COSINE: 'coseno', L2: 'euclidea (L2)', INNER_PRODUCT: 'prodotto interno' },
    datasetWarnings: 'Avvisi sui dati',
    warningCount: (count: number) => `${formatInteger(count)} righe`,
    emptyTable: 'La tabella è vuota: carica almeno un PDF nel chatbot della L17.',
    liveTest: 'Prova il modello',
    liveTestRunning: 'Prova in corso…',
    liveTestOk: (model: string, dimensions: number, millis: number) =>
      `Il modello ${model} risponde: ${formatInteger(dimensions)} dimensioni in ${formatInteger(millis)} ms.`,
    liveTestCost:
      'La prova fa una vera chiamata al provider (con OpenAI costa una frazione di centesimo).',
  },

  guide: {
    title: 'Come leggere il grafico',
    info: 'Una guida di un minuto per non farsi ingannare dal grafico.',
    lesson: 'L18 §2.2–2.3 (embedding)',
    paragraphs: [
      'Ogni punto è un chunk: un pezzo di circa 500 token di un PDF caricato nel chatbot (L17).',
      'Il suo embedding è una lista di 1536 numeri, la «coordinata GPS del significato» (L18 §2.3): testi che parlano di cose simili hanno coordinate vicine.',
      'Non possiamo disegnare 1536 dimensioni. Mostriamo l’«ombra» dei punti sulle tre direzioni in cui differiscono di più (PCA), come l’ombra di un oggetto su un muro: utile, ma alcune distanze si schiacciano.',
      'Punti vicini di solito parlano di cose simili, ma non sempre: le distanze vere le calcola il database su tutti i 1536 numeri.',
      'Similarità = 1 − distanza del coseno. Nel RagService della L18: topK = 5 e similarityThreshold = 0,5.',
    ],
    showOverlay: 'Rivedi la guida iniziale',
  },

  firstRun: {
    title: 'Benvenuto in Embedding Explorer 3D',
    intro: 'Ogni punto è un chunk dei PDF della L17. Tre cose da provare:',
    steps: [
      'Clicca un punto per selezionare un chunk e leggerne testo e numeri.',
      'Guarda i suoi vicini veri: li calcola il database sui vettori completi, non il grafico.',
      'Simula una ricerca RAG: scrivi una domanda e guarda quali chunk entrano nel contesto.',
    ],
    caveat: 'Ricorda: le posizioni sono una proiezione. Le distanze vere sono quelle del database.',
    dismiss: 'Ho capito',
  },

  projection: {
    title: 'Proiezione',
    info: 'La PCA cerca le tre direzioni lungo cui i vettori cambiano di più e vi proietta ogni punto. La percentuale dice quanta varianza (informazione) resta visibile: il resto si perde nell’ombra. Con gli embedding di testo è normale vederne meno di un terzo.',
    lesson: 'L18 §2.3',
    method: 'Metodo',
    pca: 'PCA (calcolata dal backend)',
    explained: 'Varianza spiegata',
    component: (index: number) => `PC${index + 1}`,
    total: 'Totale',
    totalExplained: (ratio: number) =>
      `Il grafico mostra il ${formatPercent(ratio)} della varianza: il ${formatPercent(1 - ratio)} non si vede.`,
    normalize: 'Normalizza i vettori',
    normalizeHint:
      'Porta ogni vettore a lunghezza 1, così il grafico rispetta la distanza del coseno.',
    normalizeOnlyCosine: 'Disponibile solo con la metrica del coseno.',
    pointSize: 'Dimensione dei punti',
    note: 'Le posizioni sono una proiezione: le distanze vere sono quelle calcolate dal database.',
    notConverged:
      'La PCA non ha raggiunto la precisione richiesta: i dati non hanno direzioni dominanti.',
  },

  colors: {
    title: 'Colori e filtri',
    info: 'Colora i chunk per file, pagina o per qualunque chiave dei metadata (per esempio department, L18 §7). I filtri attenuano i punti esclusi; la lista in fondo è l’alternativa accessibile al grafico: puoi selezionare i chunk anche da qui.',
    lesson: 'L17 (metadata), L18 §7 e §11',
    colorBy: 'Colora per',
    options: {
      file: 'File',
      page: 'Pagina',
      chunkIndex: 'Indice del chunk',
      contentLength: 'Lunghezza del testo',
    },
    metadataGroup: 'Metadata',
    legend: 'Legenda',
    legendHint: 'Clic: mostra o nascondi · Alt+clic: isola',
    showAll: 'Mostra tutti',
    missingValue: '(nessun valore)',
    hidden: 'nascosto',
    categoryLabel: (label: string, count: number, hidden: boolean) =>
      `${label}: ${formatInteger(count)} chunk${hidden ? ', nascosto' : ''}`,
    sequentialRange: (min: string, max: string) => `da ${min} a ${max}`,
    filters: 'Filtri',
    fileFilter: 'File',
    allFiles: 'Tutti i file',
    pageRange: 'Pagine',
    pageFrom: 'da',
    pageTo: 'a',
    textSearch: 'Cerca nel testo',
    textSearchPlaceholder: 'es. ferie',
    textSearchHint: 'Almeno 2 caratteri; cerca nel testo completo dei chunk.',
    textMatches: (count: number, truncated: boolean) =>
      count === 0
        ? 'Nessun chunk contiene questo testo.'
        : `${formatInteger(count)}${truncated ? '+' : ''} chunk contengono il testo cercato.`,
    resetFilters: 'Azzera i filtri',
    list: 'Chunk visibili',
    listCount: (shown: number, total: number) =>
      `${formatInteger(shown)} di ${formatInteger(total)}`,
    listEmpty: 'Nessun chunk corrisponde ai filtri.',
    pageShort: (page: number | null) => (page === null ? 'pagina ?' : `p. ${formatInteger(page)}`),
  },

  chunk: {
    title: 'Chunk selezionato',
    info: 'Il chunk come è salvato nel database: testo, metadata e i numeri del suo embedding, disegnati come un «codice a barre». I vicini sono calcolati da PostgreSQL sui vettori completi, non sul grafico.',
    lesson: 'L17 (similarity search), L18 §2.2',
    empty: 'Clicca un punto nel grafico o un chunk nella lista per vederne i dettagli.',
    file: 'File',
    page: 'Pagina',
    chunkIndex: 'Indice del chunk',
    deselect: 'Deseleziona',
    showInView: 'Mostra nel grafico',
    useAsA: 'Usa come A',
    useAsB: 'Usa come B',
    text: 'Testo',
    metadata: 'Metadata',
    metadataEmpty: 'Nessun metadata.',
    key: 'Chiave',
    value: 'Valore',
    vector: 'Vettore',
    stats: {
      dimensions: 'Dimensioni',
      norm: 'Norma (lunghezza)',
      min: 'Minimo',
      max: 'Massimo',
      mean: 'Media',
    },
    firstNumbers: 'I primi 10 numeri',
    barcode: 'Codice a barre del vettore',
    barcodeDescription: (dimensions: number, columns: number, rows: number) =>
      `Ogni quadratino è uno dei ${formatInteger(dimensions)} numeri (griglia ${formatInteger(columns)} × ${formatInteger(rows)}): blu i negativi, rosso i positivi, bianco lo zero.`,
    noVector: 'Questa riga non ha un embedding.',
    neighbors: 'Vicini veri',
    neighborsHint: 'Calcolati dal database su tutti i numeri, con la metrica del dataset.',
    neighborsK: 'Quanti',
    similarity: 'Similarità',
    showSql: 'Mostra la query SQL',
    exactSearch:
      'Ricerca esatta: il database confronta il vettore con tutti gli altri (il chatbot usa invece l’indice HNSW, approssimato ma più veloce).',
    notInSample: 'non nel campione',
    rank: (rank: number) => `${formatInteger(rank)}.`,
  },

  compare: {
    title: 'Confronta due chunk',
    info: 'La similarità vera è calcolata da PostgreSQL su tutti i numeri dei due vettori; la distanza nel grafico solo sulle 3 coordinate della proiezione. Possono non essere d’accordo: è il prezzo della proiezione.',
    lesson: 'L18 §2.3',
    slotA: 'A',
    slotB: 'B',
    pickSelected: 'Usa il selezionato',
    clear: 'Svuota',
    emptySlot: 'Seleziona un chunk e premi «Usa il selezionato».',
    real: 'Nello spazio vero',
    projected: 'Nel grafico 3D',
    cosineSimilarity: 'Similarità del coseno',
    cosineDistance: 'Distanza del coseno',
    euclideanDistance: 'Distanza euclidea',
    dotProduct: 'Prodotto scalare',
    distance3d: 'Distanza nel grafico',
    shownShare: (ratio: number) =>
      `Il grafico mostra il ${formatPercent(ratio, 0)} della distanza vera.`,
    why: 'La proiezione può solo schiacciare le distanze: due chunk vicini nel grafico possono essere lontani nello spazio vero, perché differiscono lungo direzioni che il grafico non mostra.',
    sameChunk: 'A e B sono lo stesso chunk.',
    notInView: 'Uno dei due chunk non è nel grafico (campione): la distanza 3D non è disponibile.',
  },

  rag: {
    title: 'Simula una ricerca RAG',
    info: 'Come il RagService della L18: la domanda diventa un embedding, il database cerca i topK chunk più vicini e scarta quelli sotto la soglia di similarità. Solo quelli rimasti entrano nel contesto del modello.',
    lesson: 'L18 §2.4 (contesto) e §5 (RAG advisor)',
    question: 'Domanda',
    questionPlaceholder: 'es. Quanti giorni di ferie posso chiedere?',
    topK: 'topK',
    threshold: 'similarityThreshold',
    search: 'Cerca',
    searching: 'Ricerca in corso…',
    results: 'Risultati (dal più simile)',
    passes: 'Entra nel contesto',
    discarded: 'Scartato dalla soglia',
    summary: (passing: number, total: number) =>
      `${formatInteger(passing)} di ${formatInteger(total)} chunk entrano nel contesto.`,
    sql: 'La query che Spring AI esegue',
    sqlParameters: (threshold: number, topK: number) =>
      `Parametri: il vettore della domanda (due volte), ${formatNumber(1 - threshold, 2)} (= 1 − soglia) e ${formatInteger(topK)} (topK).`,
    latency: (model: string, millis: number) =>
      `Embedding calcolato da ${model} in ${formatInteger(millis)} ms.`,
    cost: 'Ogni ricerca chiama il modello di embedding: con OpenAI costa una frazione di centesimo, per questo c’è un limite di ricerche al minuto.',
    sameModel:
      'La domanda deve essere trasformata con lo stesso modello usato per indicizzare la tabella (L18 §12.1).',
    disabledTitle: 'Simulazione disattivata',
    disabledText: 'Per attivarla imposta nel file .env:',
    disabledVariables: [
      'VIZ_EMBEDDING_ENABLED=true',
      'VIZ_EMBEDDING_BASE_URL (OpenAI, Ollama o Docker Model Runner)',
      'VIZ_EMBEDDING_MODEL (lo stesso modello usato per indicizzare)',
      'VIZ_EMBEDDING_API_KEY (solo per OpenAI)',
    ],
    disabledRestart: 'Poi riavvia il backend: docker compose up -d.',
    questionMarker: 'Domanda',
    approximate: 'posizione approssimata',
    clear: 'Togli la ricerca dal grafico',
  },

  exercises: {
    title: 'Esercizi',
    info: 'Piccole attività da fare in classe, una alla volta. Ognuna allena un’idea delle lezioni L17–L18.',
    lesson: 'L17–L18',
    items: [
      {
        title: 'Vicini nel grafico, lontani nella realtà',
        text: 'Trova due punti molto vicini nel grafico e confrontali in «Confronta due chunk». La similarità vera è alta? Se no, spiega perché la proiezione li ha avvicinati.',
      },
      {
        title: 'Dove finisce un nuovo PDF?',
        text: 'Carica un nuovo PDF nel chatbot della L17. Il grafico si aggiorna da solo: dove si collocano i nuovi chunk? Vicino a quali file?',
      },
      {
        title: 'topK e soglia',
        text: 'Fai la stessa domanda con topK 3 e 8, poi con soglia 0,3 e 0,7. Quanti chunk entrano nel contesto? Quali vengono scartati e perché?',
      },
      {
        title: 'Quanto perde la proiezione?',
        text: 'Leggi la varianza spiegata in «Proiezione». Quanta informazione vedi davvero? Che cosa resta nascosto?',
      },
      {
        title: 'Il metadata department',
        text: 'Colora per department (L18 §7): i reparti formano gruppi separati o si mescolano? Che cosa significa per un filtro sui metadata?',
      },
      {
        title: 'Due modelli, due mappe',
        text: 'Punta lo strumento a un’altra tabella della L18 §12 (VIZ_DB_TABLE, per esempio vector_store_bge_m3) e confronta la forma della nuvola e i vicini dello stesso testo.',
      },
    ],
  },

  scene: {
    toolbar: 'Comandi della vista 3D',
    resetView: 'Reimposta vista',
    grid: 'Griglia',
    screenshot: 'Salva immagine',
    loading: 'Caricamento della vista 3D…',
    loadingPoints: 'Caricamento dei punti…',
    reloadingPoints: 'Aggiornamento dei punti…',
    axis: (index: number, ratio: number | null) =>
      `PC${index + 1}` + (ratio === null ? '' : ` · ${formatPercent(ratio)}`),
    tooltipPage: (page: number | null) =>
      page === null ? 'pagina ?' : `pagina ${formatInteger(page)}`,
    projectionBadge: 'Proiezione PCA: le distanze nel grafico sono approssimate.',
    noWebgl: 'Il browser non supporta WebGL 2, quindi la vista 3D non è disponibile.',
    noWebglHint:
      'Usa un browser recente (Chrome, Firefox, Edge, Safari) o attiva l’accelerazione hardware. Puoi comunque usare la lista dei chunk nella barra laterale.',
    contextLost: 'La vista 3D si è interrotta (la scheda grafica ha perso il contesto WebGL).',
    crashed: 'La vista 3D ha avuto un errore.',
    reload: 'Ricarica la vista',
    noData: 'Nessun punto da mostrare.',
    help: 'Trascina per ruotare · rotella per lo zoom · tasto destro per spostare · doppio clic per centrare un punto',
  },

  errors: {
    VALIDATION_FAILED: {
      message: 'La richiesta non è valida.',
      hint: 'Controlla i valori inseriti (per esempio k tra 1 e 50, testo tra 2 e 200 caratteri).',
    },
    NOT_FOUND: {
      message: 'Questa funzione non esiste sul backend.',
      hint: 'Frontend e backend potrebbero avere versioni diverse: ricostruisci le immagini con «docker compose up --build».',
    },
    CHUNK_NOT_FOUND: {
      message: 'Il chunk non esiste più.',
      hint: 'Forse il PDF è stato reindicizzato: ricarica i punti e selezionane un altro.',
    },
    METHOD_NOT_ALLOWED: {
      message: 'Operazione non consentita.',
      hint: 'Frontend e backend potrebbero avere versioni diverse: ricostruisci le immagini con «docker compose up --build».',
    },
    DATASET_CHANGED: {
      message: 'I dati sono cambiati mentre li leggevamo.',
      hint: 'Ricarico i punti automaticamente.',
    },
    DIMENSION_MISMATCH: {
      message: 'Le dimensioni dei vettori non coincidono.',
      hint: 'Usa una tabella per ogni modello (L18 §12) e cerca con lo stesso modello usato per indicizzare (VIZ_EMBEDDING_MODEL).',
    },
    PROJECTION_NOT_POSSIBLE: {
      message: 'Non ci sono abbastanza dati per la proiezione 3D.',
      hint: 'Servono almeno 3 chunk diversi: carica un PDF nel chatbot della L17.',
    },
    RATE_LIMITED: {
      message: 'Troppe ricerche in poco tempo.',
      hint: 'Aspetta qualche secondo: il limite protegge il servizio di embedding a pagamento (VIZ_SEARCH_RATE_LIMIT_PER_MINUTE).',
    },
    EMBEDDING_RATE_LIMITED: {
      message: 'Il provider di embedding ha rifiutato la richiesta: troppe chiamate.',
      hint: 'Aspetta un minuto o controlla i limiti del tuo account.',
    },
    INTERNAL_ERROR: {
      message: 'Errore inatteso del backend.',
      hint: 'Riprova; se succede ancora, cerca il codice della richiesta nei log di viz-backend.',
    },
    EMBEDDING_PROVIDER_ERROR: {
      message: 'Il servizio di embedding ha risposto con un errore.',
      hint: 'Controlla VIZ_EMBEDDING_BASE_URL e che il servizio sia raggiungibile (per Ollama: «ollama serve» e il modello scaricato).',
    },
    EMBEDDING_AUTH_FAILED: {
      message: 'Il servizio di embedding ha rifiutato la chiave.',
      hint: 'Controlla VIZ_EMBEDDING_API_KEY nel file .env e riavvia il backend.',
    },
    EMBEDDING_MODEL_NOT_FOUND: {
      message: 'Il modello di embedding non esiste sul provider.',
      hint: 'Controlla VIZ_EMBEDDING_MODEL: deve essere lo stesso modello usato per indicizzare la tabella.',
    },
    DB_UNAVAILABLE: {
      message: 'Il database vettoriale non risponde.',
      hint: 'Avvia il database della L17 («docker compose up -d» nella cartella docker) e controlla VIZ_DB_URL.',
    },
    DB_AUTH_FAILED: {
      message: 'Il database ha rifiutato utente o password.',
      hint: 'Controlla VIZ_DB_USER e VIZ_DB_PASSWORD nel file .env (nella L17: chatbot / chatbot123).',
    },
    DB_PERMISSION_DENIED: {
      message: 'L’utente del database non può leggere la tabella.',
      hint: 'Concedi il permesso: GRANT SELECT ON ALL TABLES IN SCHEMA public TO viz_reader;',
    },
    DB_NOT_FOUND: {
      message: 'Il database indicato non esiste.',
      hint: 'Controlla il nome del database alla fine di VIZ_DB_URL (nella L17 è «chatbot»).',
    },
    PGVECTOR_MISSING: {
      message: 'Nel database manca l’estensione pgvector.',
      hint: 'Usa l’immagine pgvector/pgvector:pg17 ed esegui «CREATE EXTENSION vector;» (lo fa l’init.sql della L17).',
    },
    TABLE_NOT_FOUND: {
      message: 'La tabella dei vettori non esiste.',
      hint: 'Avvia il chatbot almeno una volta (crea vector_store) o controlla VIZ_DB_SCHEMA e VIZ_DB_TABLE.',
    },
    TABLE_SCHEMA_INVALID: {
      message: 'La tabella non ha la struttura creata da Spring AI.',
      hint: 'Servono le colonne id, content, metadata (json) ed embedding (vector): controlla VIZ_DB_TABLE.',
    },
    SNAPSHOT_TOO_LARGE: {
      message: 'Troppi vettori da caricare in memoria.',
      hint: 'Riduci VIZ_MAX_POINTS o aumenta VIZ_SNAPSHOT_MEMORY_BUDGET_MB.',
    },
    EMBEDDING_DISABLED: {
      message: 'La simulazione RAG è disattivata.',
      hint: 'Imposta VIZ_EMBEDDING_ENABLED=true e il modello nel file .env, poi riavvia il backend.',
    },
    DB_TIMEOUT: {
      message: 'Il database ha impiegato troppo tempo.',
      hint: 'La query ha superato il timeout: riprova; con tabelle molto grandi riduci VIZ_MAX_POINTS.',
    },
    EMBEDDING_TIMEOUT: {
      message: 'Il servizio di embedding non ha risposto in tempo.',
      hint: 'Riprova; con Ollama il primo caricamento del modello può richiedere qualche secondo.',
    },
    NETWORK_ERROR: {
      message: 'Impossibile contattare il server.',
      hint: 'Controlla che i container siano avviati («docker compose ps») e la connessione.',
    },
    TIMEOUT: {
      message: 'Il server non ha risposto in tempo.',
      hint: 'Riprova tra qualche secondo.',
    },
    BACKEND_UNAVAILABLE: {
      message: 'Il backend non risponde.',
      hint: 'Il container viz-backend si sta avviando o è fermo: controlla «docker compose ps» e «docker compose logs viz-backend».',
    },
    INVALID_RESPONSE: {
      message: 'Risposta inattesa dal server.',
      hint: 'Frontend e backend potrebbero avere versioni diverse: ricostruisci con «docker compose up --build».',
    },
    UNEXPECTED_STATUS: {
      message: 'Il server ha risposto con un errore inatteso.',
      hint: 'Riprova; se succede ancora, controlla i log del backend.',
    },
  } satisfies Record<string, MessageWithHint>,

  unknownError: (code: string) => ({
    message: `Errore imprevisto (${code}).`,
    hint: 'Riprova; se succede ancora, comunica al docente il codice della richiesta.',
  }),

  warnings: {
    TABLE_EMPTY: {
      message: 'La tabella è vuota.',
      hint: 'Carica almeno un PDF nel chatbot della L17.',
    },
    NULL_EMBEDDINGS_SKIPPED: {
      message: 'Alcune righe non hanno un embedding e vengono ignorate.',
      hint: 'Succede se l’indicizzazione si è interrotta: ricarica il PDF nel chatbot.',
    },
    DIMENSIONS_MIXED: {
      message: 'Nella tabella ci sono vettori di dimensioni diverse.',
      hint: 'Ogni modello deve avere la sua tabella (L18 §12).',
    },
    INDEX_MISSING: {
      message: 'Manca l’indice sul vettore.',
      hint: 'Le ricerche restano corrette, solo più lente. Spring AI lo crea con initialize-schema=true.',
    },
    METRIC_DIFFERS_FROM_INDEX: {
      message: 'La metrica configurata è diversa da quella dell’indice.',
      hint: 'Imposta VIZ_DISTANCE_METRIC=auto o la stessa distance-type usata dal chatbot.',
    },
    FILE_KEY_MISSING: {
      message: 'I metadata non dicono da quale file viene ogni chunk.',
      hint: 'Mancano original_filename, file_name e source: i chunk finiscono nel file «Sconosciuto».',
    },
    METADATA_NOT_OBJECT: {
      message: 'Alcuni metadata non sono oggetti JSON e sono trattati come vuoti.',
      hint: 'Controlla come vengono creati i Document nell’ingestion (L18 §11).',
    },
    CONTENT_NULL: {
      message: 'Alcuni chunk non hanno testo.',
      hint: 'Controlla la lettura del PDF: forse alcune pagine sono immagini.',
    },
    EMBEDDING_KEY_MISSING: {
      message: 'Manca la chiave API di OpenAI.',
      hint: 'Imposta VIZ_EMBEDDING_API_KEY nel file .env, oppure usa un provider locale (Ollama).',
    },
  } satisfies Record<string, MessageWithHint>,
} as const;

export type Messages = typeof it;
