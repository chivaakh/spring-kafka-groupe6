# 🎯 Explication Simple du Projet Spring Boot + Kafka

## Groupe 6 - Système de Traitement de Commandes

---

## 📌 C'est quoi ce projet ?

C'est un **système de commandes en ligne** qui utilise **Kafka** comme "facteur" pour transporter les messages.

Imagine un restaurant :

```
👤 Client → 📝 Commande → 🍳 Cuisine → ✅ Livré
```

Sauf qu'ici, tout passe par **Kafka** (un système de messagerie ultra-rapide).

---

## 🍕 Analogie de la Pizzeria

| Élément réel | Dans le projet |
|--------------|----------------|
| Client qui commande | Utilisateur via navigateur web |
| Serveur qui prend la commande | `OrderProducerService` |
| Ticket de commande | `Order.java` |
| Cuisine | `OrderConsumerService` |
| Règles de la cuisine | `KafkaConsumerConfig` |
| Poubelle pour commandes ratées | `DLQConsumerService` |

---

## 📂 Explication de TOUS les fichiers

---

### 1️⃣ Order.java - Le bon de commande 📝

C'est la **fiche de commande**. Elle contient :

| Champ | C'est quoi ? | Exemple |
|-------|-------------|---------|
| `id` | Numéro unique de la commande | "abc-123" |
| `customerId` | Qui a commandé | "CUST-456" |
| `items` | Liste des articles | ["Pizza", "Coca"] |
| `totalAmount` | Prix total | 25.50€ |
| `status` | État de la commande | "PENDING", "COMPLETED" |
| `timestamp` | Quand | 1702840800000 |

**Code simplifié :**
```java
public class Order {
    private String id;           // Numéro de commande
    private String customerId;   // Qui commande
    private List<String> items;  // Quoi
    private double totalAmount;  // Combien
    private String status;       // État
    private long timestamp;      // Quand
}
```

---

### 2️⃣ OrderController.java - L'entrée du restaurant 🚪

C'est la **porte d'entrée** pour créer des commandes via le navigateur web.

| URL | Que fait-il ? |
|-----|--------------|
| `POST /api/orders` | Envoie une commande que tu crées toi-même |
| `GET /api/orders/generate` | Génère automatiquement une commande aléatoire |

**Exemple :** Va sur `http://localhost:8080/api/orders/generate` → ça crée une commande !

**Code simplifié :**
```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/generate")
    public String generate() {
        // Crée une commande aléatoire
        Order order = new Order(...);
        // L'envoie vers Kafka
        producerService.sendOrder(order);
        return "Commande générée!";
    }
}
```

---

### 3️⃣ OrderProducerService.java - Le serveur qui prend la commande 📤

C'est le **serveur** qui :
1. Prend ta commande
2. L'envoie à la cuisine (Kafka)

```
Client → OrderProducerService → Kafka (topic "orders-input")
```

**Code simplifié :**
```java
@Service
public class OrderProducerService {

    public void sendOrder(Order order) {
        // Envoie la commande vers le topic Kafka "orders-input"
        kafkaTemplate.send("orders-input", order.getId(), order);
    }
}
```

---

### 4️⃣ OrderConsumerService.java - Le cuisinier 👨‍🍳

C'est **le cœur du travail de EMANE (Tâche 4)**. Il :

| Étape | Action |
|-------|--------|
| 1 | Reçoit la commande de Kafka |
| 2 | Vérifie si elle est valide (ID, client, articles, montant) |
| 3 | Change le statut : PENDING → PROCESSING |
| 4 | Vérifie le stock |
| 5 | Change le statut : PROCESSING → COMPLETED |
| 6 | Envoie vers "orders-processed" |

**Règles de validation :**

| Champ | Règle |
|-------|-------|
| ID | Obligatoire, non vide |
| Customer ID | Obligatoire, non vide |
| Items | Au moins 1 article |
| Montant | Entre 0.01€ et 10000€ |

**Code simplifié :**
```java
@Service
public class OrderConsumerService {

    @KafkaListener(topics = "orders-input")
    public void consumeOrder(Order order) {
        // 1. Valider la commande
        validateOrder(order);
        
        // 2. Changer statut en PROCESSING
        order.setStatus("PROCESSING");
        
        // 3. Vérifier le stock
        checkStock(order);
        
        // 4. Changer statut en COMPLETED
        order.setStatus("COMPLETED");
        
        // 5. Publier vers orders-processed
        kafkaTemplate.send("orders-processed", order);
    }
}
```

---

### 5️⃣ KafkaConsumerConfig.java - Les règles de la cuisine ⚙️

C'est la **configuration des règles** pour gérer les erreurs :

| Règle | Valeur | Explication |
|-------|--------|-------------|
| MAX_RETRIES | 3 | Nombre de tentatives si erreur |
| INITIAL_INTERVAL | 1 seconde | Délai avant 1ère retry |
| MULTIPLIER | 2.0 | Délai x2 à chaque retry |
| MAX_INTERVAL | 10 secondes | Délai maximum |

**Comment ça marche (Exponential Backoff) :**
```
Tentative 1 : échec → attendre 1 seconde
Tentative 2 : échec → attendre 2 secondes  
Tentative 3 : échec → attendre 4 secondes
Après 3 échecs → envoyer vers DLQ (poubelle)
```

**Code simplifié :**
```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public CommonErrorHandler errorHandler() {
        // Configuration: 3 tentatives avec backoff exponentiel
        ExponentialBackOffWithMaxRetries backOff = 
            new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000L);  // 1 seconde
        backOff.setMultiplier(2.0);         // x2 à chaque fois
        
        // Si 3 échecs → envoyer vers DLQ
        return new DefaultErrorHandler(deadLetterPublishingRecoverer(), backOff);
    }
}
```

---

### 6️⃣ DLQConsumerService.java - La poubelle spéciale 🗑️

**DLQ = Dead Letter Queue** = File d'attente des messages "morts" (en erreur)

C'est le **service qui récupère les commandes ratées** :

- Écoute le topic `orders-dlq`
- Affiche les détails de l'erreur
- Pourrait envoyer une alerte ou sauvegarder en base de données

**Code simplifié :**
```java
@Service
public class DLQConsumerService {

    @KafkaListener(topics = "orders-dlq")
    public void consumeDLQMessage(ConsumerRecord<String, Order> record) {
        // Récupérer les infos d'erreur
        String exceptionClass = getHeader("kafka_dlt-exception-fqcn");
        String exceptionMessage = getHeader("kafka_dlt-exception-message");
        
        // Afficher les détails
        logger.error("Commande en erreur reçue!");
        logger.error("Exception: " + exceptionClass);
        logger.error("Message: " + exceptionMessage);
    }
}
```

---

### 7️⃣ Fichiers d'exception

| Fichier | Quand ? | Retry ? |
|---------|---------|---------|
| `OrderValidationException.java` | Commande invalide (pas d'ID, montant négatif) | ❌ Non |
| `StockUnavailableException.java` | Pas de stock disponible | ✅ Oui (3 fois) |

---

## 🔄 Flux Complet du Système

```
┌──────────────────┐
│  Utilisateur     │
│  (navigateur)    │
└────────┬─────────┘
         │ GET /api/orders/generate
         ▼
┌──────────────────┐
│ OrderController  │  ← Crée la commande
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ OrderProducer    │  ← Envoie vers Kafka
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│     KAFKA        │  ← Topic "orders-input"
│  (facteur)       │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ OrderConsumer    │  ← Traite + valide
│ (Tâche EMANE)    │
└────────┬─────────┘
         │
    OK ? ├──────────────────┐
         │                  │
         ▼                  ▼
┌──────────────────┐  ┌──────────────────┐
│ orders-processed │  │    orders-dlq    │
│    (succès)      │  │    (erreurs)     │
└──────────────────┘  └──────────────────┘
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

## ✅ Résumé : Ce que EMANE a fait (Tâche 4)

| Composant | Description |
|-----------|-------------|
| `OrderConsumerService.java` | Traitement des commandes |
| `KafkaConsumerConfig.java` | Configuration retry + DLQ |
| `DLQConsumerService.java` | Gestion des erreurs |
| `OrderValidationException.java` | Exception validation |
| `StockUnavailableException.java` | Exception stock |
| Tests unitaires | 10 tests passés |

**En UNE phrase :**
> EMANE a codé le programme qui reçoit les commandes, les vérifie, gère les erreurs avec retry automatique, et met les commandes ratées dans une poubelle spéciale (DLQ).

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

### 3. Générer une commande
Ouvrir dans le navigateur : `http://localhost:8080/api/orders/generate`

### 4. Observer les logs
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

## 📦 Technologies Utilisées

| Technologie | Utilisation |
|-------------|-------------|
| Spring Boot | Framework Java |
| Spring Kafka | Intégration Kafka |
| Apache Kafka | Système de messagerie |
| Docker | Conteneur pour Kafka |
| JUnit 5 | Tests unitaires |

---

## 👤 Auteur

**EMANE** - Tâche 4 : Traitement, Erreurs et Retry

Date : Décembre 2025

---

## 📄 Comment Convertir ce fichier en PDF

### Option 1 - VS Code
1. Installer l'extension "Markdown PDF"
2. Clic droit sur le fichier → "Markdown PDF: Export (pdf)"

### Option 2 - En ligne
1. Aller sur https://markdowntopdf.com
2. Coller le contenu
3. Télécharger le PDF

### Option 3 - Pandoc (ligne de commande)
```bash
pandoc EXPLICATION_SIMPLE_PROJET.md -o EXPLICATION_SIMPLE_PROJET.pdf
```
