# 📋 Tâche 4 - Traitement, Erreurs et Retry

## Responsable : EMANE

---

## 📝 Résumé Simple

La **Tâche 4** consiste à créer le système qui **traite les commandes** reçues via Kafka. Quand une commande arrive, on la valide, on vérifie le stock, on change son statut, et on la publie vers un autre topic. Si ça échoue, on réessaye automatiquement, et si ça échoue encore, on envoie le message dans une "file d'attente des erreurs" (DLQ).

---

## 🎯 Objectif

Implémenter le **traitement complet des commandes** avec :
- Validation des données
- Gestion des erreurs avec retry automatique
- Dead Letter Queue (DLQ) pour les messages en échec

---

## 📁 Fichiers Créés/Modifiés

| Fichier | Description |
|---------|-------------|
| `OrderConsumerService.java` | Service principal qui traite les commandes |
| `KafkaConsumerConfig.java` | Configuration du Consumer avec gestion des erreurs |
| `DLQConsumerService.java` | Service qui écoute les messages en erreur |
| `OrderValidationException.java` | Exception pour les erreurs de validation |
| `StockUnavailableException.java` | Exception pour les erreurs de stock |
| `OrderConsumerTest.java` | Tests unitaires |
| `OrderProcessingIntegrationTest.java` | Tests d'intégration |

---

## 🔄 Flux de Traitement

```
┌─────────────────┐
│  orders-input   │  ← Commande reçue
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   VALIDATION    │  ← Vérifier ID, client, items, montant
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ STATUT=PROCESSING│  ← Changer le statut
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ VÉRIF. STOCK    │  ← Simuler la vérification du stock
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ STATUT=COMPLETED│  ← Commande traitée avec succès
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│orders-processed │  ← Publier la commande terminée
└─────────────────┘
```

---

## 🔧 PARTIE A : Logique de Traitement

### Fichier : `OrderConsumerService.java`

**Que fait-il ?**
1. **Reçoit** les messages du topic `orders-input`
2. **Valide** la commande (ID, client, items, montant)
3. **Vérifie** le stock (simulation)
4. **Change** le statut : `PENDING` → `PROCESSING` → `COMPLETED`
5. **Publie** vers `orders-processed`

### Règles de Validation

| Champ | Règle |
|-------|-------|
| ID | Obligatoire, non vide |
| Customer ID | Obligatoire, non vide |
| Items | Au moins 1 article |
| Montant | Entre 0.01€ et 10000€ |

### Code Clé

```java
@KafkaListener(topics = "orders-input", groupId = "order-consumer-group")
public void consumeOrder(Order order, ...) {
    // 1. Valider la commande
    validateOrder(order);
    
    // 2. Changer statut en PROCESSING
    order.setStatus("PROCESSING");
    
    // 3. Vérifier le stock
    checkStock(order);
    
    // 4. Changer statut en COMPLETED
    order.setStatus("COMPLETED");
    
    // 5. Publier vers orders-processed
    kafkaTemplate.send("orders-processed", order.getId(), order);
}
```

---

## ⚠️ PARTIE B : Gestion des Erreurs

### Fichier : `KafkaConsumerConfig.java`

**Que fait-il ?**
Configure le système de **retry automatique** quand une erreur survient.

### Configuration

| Paramètre | Valeur | Description |
|-----------|--------|-------------|
| MAX_RETRIES | 3 | Nombre de tentatives |
| INITIAL_INTERVAL | 1 seconde | Délai avant 1ère retry |
| MULTIPLIER | 2.0 | Multiplicateur (1s → 2s → 4s) |
| MAX_INTERVAL | 10 secondes | Délai maximum |

### Exponential Backoff

```
Tentative 1 : échec → attendre 1 seconde
Tentative 2 : échec → attendre 2 secondes  
Tentative 3 : échec → attendre 4 secondes
Après 3 échecs → envoyer vers DLQ
```

### Exceptions Non-Retriables

`OrderValidationException` ne déclenche **pas** de retry car c'est une erreur de données (inutile de réessayer avec les mêmes données incorrectes).

### Code Clé

```java
@Bean
public CommonErrorHandler errorHandler() {
    ExponentialBackOffWithMaxRetries backOff = 
        new ExponentialBackOffWithMaxRetries(3);
    backOff.setInitialInterval(1000L);  // 1 seconde
    backOff.setMultiplier(2.0);
    
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(
        deadLetterPublishingRecoverer(),
        backOff
    );
    
    // Pas de retry pour les erreurs de validation
    errorHandler.addNotRetryableExceptions(OrderValidationException.class);
    
    return errorHandler;
}
```

---

## 💀 PARTIE C : Dead Letter Queue (DLQ)

### Concept

La **Dead Letter Queue** est une file d'attente spéciale où vont les messages qui ont échoué après toutes les tentatives de retry. C'est comme une "boîte aux lettres mortes" pour les messages problématiques.

### Fichier : `DLQConsumerService.java`

**Que fait-il ?**
1. Écoute le topic `orders-dlq`
2. Log les informations détaillées de l'erreur
3. Extrait les headers d'erreur (exception, timestamp, etc.)

### Headers DLQ

| Header | Description |
|--------|-------------|
| `kafka_dlt-exception-fqcn` | Nom complet de l'exception |
| `kafka_dlt-exception-message` | Message d'erreur |
| `kafka_dlt-original-topic` | Topic d'origine |
| `kafka_dlt-original-partition` | Partition d'origine |
| `kafka_dlt-original-offset` | Offset d'origine |
| `kafka_dlt-original-timestamp` | Timestamp d'origine |

### Code Clé

```java
@KafkaListener(topics = "orders-dlq", groupId = "dlq-consumer-group")
public void consumeDLQMessage(ConsumerRecord<String, Order> record) {
    // Extraire les informations d'erreur
    String exceptionClass = getHeaderValue(headers, "kafka_dlt-exception-fqcn");
    String exceptionMessage = getHeaderValue(headers, "kafka_dlt-exception-message");
    
    // Logger les détails
    logger.error("Message en erreur reçu dans la DLQ");
    logger.error("Exception: {}", exceptionClass);
    logger.error("Message: {}", exceptionMessage);
}
```

---

## 🧪 PARTIE D : Tests

### Tests Unitaires (`OrderConsumerTest.java`)

| Test | Description |
|------|-------------|
| ✅ `shouldProcessValidOrderSuccessfully` | Traite une commande valide |
| ✅ `shouldChangeStatusFromPendingToCompleted` | Vérifie le changement de statut |
| ✅ `shouldRejectOrderWithoutId` | Rejette si pas d'ID |
| ✅ `shouldRejectOrderWithoutCustomerId` | Rejette si pas de client |
| ✅ `shouldRejectOrderWithoutItems` | Rejette si pas d'articles |
| ✅ `shouldRejectOrderWithNegativeAmount` | Rejette si montant < 0.01€ |
| ✅ `shouldRejectOrderWithExcessiveAmount` | Rejette si montant > 10000€ |
| ✅ `shouldAcceptOrderWithMinimumAmount` | Accepte montant = 0.01€ |
| ✅ `shouldAcceptOrderWithMaximumAmount` | Accepte montant = 10000€ |
| ✅ `shouldPublishToOrdersProcessedTopic` | Vérifie la publication |

### Exécution des Tests

```bash
cd order-system
.\mvnw.cmd test -Dtest=OrderConsumerTest
```

**Résultat :**
```
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 🚀 Comment Tester

### 1. Démarrer Kafka (Docker)
```bash
cd order-system
docker-compose up -d
```

### 2. Démarrer l'application
```bash
.\mvnw.cmd spring-boot:run
```

### 3. Envoyer une commande
```bash
curl http://localhost:8080/api/orders/generate
```

### 4. Observer les logs
Vous verrez le flux complet :
```
========================================
Message reçu du topic 'orders-input'
Order ID: xxx-xxx-xxx
✓ Validation réussie
→ Statut changé en PROCESSING
✓ Stock vérifié
✓ Statut changé en COMPLETED
✓✓ Commande traitée avec succès!
========================================
```

---

## 📊 Architecture des Topics Kafka

```
┌─────────────────────────────────────────────────────────────┐
│                        KAFKA                                 │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│   │ orders-input │    │orders-processed│   │  orders-dlq  │  │
│   │              │    │              │    │              │  │
│   │  Commandes   │    │  Commandes   │    │  Messages    │  │
│   │  entrantes   │    │  terminées   │    │  en erreur   │  │
│   └──────────────┘    └──────────────┘    └──────────────┘  │
│         │                    ▲                   ▲          │
│         │                    │                   │          │
│         └───── Consumer ─────┴───── Erreur ──────┘          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 📦 Technologies Utilisées

| Technologie | Utilisation |
|-------------|-------------|
| Spring Kafka | Framework Kafka pour Spring |
| @KafkaListener | Annotation pour consommer les messages |
| DefaultErrorHandler | Gestion des erreurs avec retry |
| ExponentialBackOffWithMaxRetries | Backoff exponentiel |
| DeadLetterPublishingRecoverer | Envoi vers DLQ |
| KafkaTemplate | Publication des messages |
| JUnit 5 + Mockito | Tests unitaires |
| EmbeddedKafka | Tests d'intégration |

---

## ✅ Livrables Complétés

- [x] Traitement complet des commandes
- [x] Publication vers 'orders-processed'
- [x] Retry configuré (3 tentatives + exponential backoff)
- [x] DLQ fonctionnel (vers 'orders-dlq')
- [x] Tests de bout en bout

---

## 👤 Auteur

**EMANE** - Tâche 4 : Traitement, Erreurs et Retry

Date : 15 Décembre 2025

---

## 📄 Comment Convertir en PDF

1. **Option 1 - VS Code** : Installer l'extension "Markdown PDF" et faire clic droit → "Markdown PDF: Export (pdf)"

2. **Option 2 - En ligne** : Copier le contenu sur [https://markdowntopdf.com](https://markdowntopdf.com)

3. **Option 3 - Pandoc** :
   ```bash
   pandoc TACHE4_DOCUMENTATION_EMANE.md -o TACHE4_DOCUMENTATION_EMANE.pdf
   ```

