# 🚀 Système de Traitement de Commandes - Apache Kafka

> Projet Spring Boot implémentant une architecture orientée événements avec Apache Kafka pour le traitement de commandes en temps réel.

## 👥 Équipe - Groupe 6

| Membre | Rôle | Responsabilité |
|--------|------|----------------|
| **Chiva** | Infrastructure | Configuration Kafka, Docker, topics, Spring Boot |
| **Zahra** | Producer | OrderProducerService, OrderController, modèle Order |
| **Wethigha** | Consumer Base | Configuration Consumer, listener basique |
| **Emane** | Traitement & Erreurs | Logique métier, retry, DLQ, gestion des erreurs |
| **Aya** | Tests & Documentation | Tests unitaires, intégration, documentation |

---


## 🎯 Description

Ce projet implémente un système de traitement de commandes e-commerce utilisant **Apache Kafka** pour la communication asynchrone entre services.

### Fonctionnalités Principales

- ✅ Créer des commandes via API REST
- ✅ Traitement asynchrone avec Kafka
- ✅ Validation métier (montant, stock, données)
- ✅ Mécanisme de retry automatique
- ✅ Dead Letter Queue (DLQ) pour erreurs persistantes
- ✅ Monitoring avec Spring Boot Actuator

### Patterns Implémentés

- **Event-Driven Architecture** - Communication par événements
- **Producer/Consumer Pattern** - Séparation des responsabilités
- **Retry Pattern** - Résilience face aux erreurs temporaires
- **Dead Letter Queue** - Gestion des erreurs persistantes

---

## 🏗️ Architecture

### Vue d'Ensemble

```
┌──────────────┐         ┌─────────────────┐         ┌──────────────────┐
│   Client     │         │   REST API      │         │   Kafka Broker   │
│   (Postman)  │────────▶│   Controller    │────────▶│                  │
└──────────────┘         └─────────────────┘         │  orders-input    │
                                                      └──────────────────┘
                                                               │
                                                               ▼
                                                      ┌──────────────────┐
                                                      │   Consumer       │
                                                      │   (Traitement)   │
                                                      └──────────────────┘
                                                               │
                                    ┌──────────────────────────┼──────────────────────────┐
                                    │                          │                          │
                                    ▼                          ▼                          ▼
                           ┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
                           │ orders-processed│       │   orders-dlq    │       │   Logs          │
                           │  (Succès)       │       │   (Erreurs)     │       │                 │
                           └─────────────────┘       └─────────────────┘       └─────────────────┘
```

### Flux de Messages

**Commande Valide ✅**
```
Client → POST /api/orders → Producer → orders-input → Consumer → Validation → orders-processed
```

**Commande Invalide ❌**
```
Client → POST /api/orders → Producer → orders-input → Consumer → Validation échoue
    → Retry (2-3 tentatives) → Échec persistant → orders-dlq
```

### Topics Kafka

| Topic | Description | Responsable |
|-------|-------------|-------------|
| `orders-input` | Réception des nouvelles commandes | Zahra (Producer) |
| `orders-processed` | Commandes traitées avec succès | Emane (Consumer) |
| `orders-dlq` | Dead Letter Queue pour erreurs | Emane (DLQ) |

---

## 📋 Prérequis

Avant de commencer, installez :

- ✅ **Java 17** ou supérieur
  ```bash
  java -version
  # Doit afficher : java version "17.x.x"
  ```

- ✅ **Maven 3.9+**
  ```bash
  mvn -version
  ```

- ✅ **Docker Desktop** (pour Kafka)
  - Télécharger depuis : https://www.docker.com/products/docker-desktop

- ✅ **Git**
  ```bash
  git --version
  ```

---

## 🚀 Installation et Démarrage

### 1️⃣ Cloner le Projet

```bash
git clone <URL_du_repo>
cd order-system
```

### 2️⃣ Démarrer Kafka avec Docker

```bash
docker-compose up -d
```

**Vérifier que les conteneurs tournent :**
```bash
docker ps
```

Vous devriez voir `kafka-broker` et `zookeeper` en statut **UP**.

### 3️⃣ Vérifier les Topics Kafka

Les 3 topics sont créés automatiquement au démarrage :

```bash
docker exec -it kafka-broker bash
kafka-topics --list --bootstrap-server localhost:9092
exit
```

Vous devriez voir :
- `orders-input`
- `orders-processed`
- `orders-dlq`

### 4️⃣ Compiler le Projet

```bash
mvn clean install
```

### 5️⃣ Lancer l'Application

```bash
mvn spring-boot:run
```

**L'application démarre sur** : `http://localhost:8080`

**Vérifier le health check :**
```bash
curl http://localhost:8080/actuator/health
# Devrait retourner : {"status":"UP"}
```

---

## 📝 Utilisation

### 1. Créer une Commande Manuellement

**Avec cURL :**
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "id": "ORDER-001",
    "customerId": "CUST-123",
    "items": ["Laptop", "Mouse", "Keyboard"],
    "totalAmount": 1299.99,
    "status": "PENDING",
    "timestamp": 1702742400000
  }'
```

**Avec Postman :**
- Méthode : `POST`
- URL : `http://localhost:8080/api/orders`
- Headers : `Content-Type: application/json`
- Body (raw JSON) : Voir exemple ci-dessus

### 2. Générer une Commande Aléatoire

```bash
curl -X GET http://localhost:8080/api/orders/generate
```

### 3. Vérifier les Logs

Les logs montrent le flux complet :

```
✅ Producer : Order sent successfully: ORDER-001
✅ Consumer : Received order: ORDER-001
✅ Consumer : Order validated: ORDER-001
✅ Consumer : Order processed: ORDER-001 with status: COMPLETED
✅ Published to orders-processed: ORDER-001
```

### 4. Tester une Commande Invalide

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "id": "ORDER-BAD",
    "customerId": "CUST-999",
    "items": [],
    "totalAmount": -100,
    "status": "PENDING",
    "timestamp": 1702742400000
  }'
```

Les logs montreront les **retries** puis le **routage vers DLQ** :
```
❌ Consumer : Validation failed for ORDER-BAD: Items cannot be empty
⚠️  Retry attempt 1/3...
⚠️  Retry attempt 2/3...
⚠️  Retry attempt 3/3...
❌ Order sent to DLQ: ORDER-BAD
```

---

## 📂 Structure du Projet

```
order-system/
├── src/main/java/com/kafka/groupe6/order_system/
│   ├── config/
│   │   └── KafkaConfig.java              
|   |   └── KafkaConsumerConfig.java  
|   |   └── KafkaTopicConfig.java  
│   ├── controller/
│   │   └── OrderController.java          
│   ├── model/
│   │   └── Order.java                    
│   ├── producer/
│   │   └── OrderProducerService.java     
│   ├── consumer/
│   │   ├── OrderConsumerService.java     
│   │   └── DLQConsumerService.java
│   ├── exception/
│   │   ├── OrderValidationException.java     
│   │   └── StockUnavailableException.java       
│   └── OrderSystemApplication.java       
├── src/main/resources/
│   └── application.yml                   
├── src/test/java/                       
├── docker-compose.yml                    
├── pom.xml                               
└── README.md
```

---

## ⚙️ Configuration

### Application (application.yml)

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      
    consumer:
      group-id: order-consumer-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      properties:
        spring.json.trusted.packages: "*"

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

### Docker Compose (docker-compose.yml)

Le fichier configure automatiquement :
- Zookeeper sur le port 2181
- Kafka Broker sur le port 9092
- Création automatique des 3 topics

### Paramètres Modifiables

| Paramètre | Valeur par Défaut | Description |
|-----------|-------------------|-------------|
| `bootstrap-servers` | localhost:9092 | Adresse du broker Kafka |
| `group-id` | order-consumer-group | Groupe de consommateurs |
| `server.port` | 8080 | Port de l'application |

---

## 🧪 Tests

### Lancer Tous les Tests

```bash
mvn test
```

### Tests Unitaires Uniquement

```bash
mvn test -Dtest="*Test"
```

### Tests d'Intégration

```bash
mvn test -Dtest="*IntegrationTest"
```

### Rapport de Couverture

```bash
mvn jacoco:report
```


## 📚 API Documentation

### Endpoints Disponibles

#### 1. Créer une Commande

**POST** `/api/orders`

**Request Body :**
```json
{
  "id": "ORDER-123",
  "customerId": "CUST-456",
  "items": ["Product A", "Product B"],
  "totalAmount": 299.99,
  "status": "PENDING",
  "timestamp": 1702742400000
}
```

**Response 200 OK :**
```json
{
  "id": "ORDER-123",
  "customerId": "CUST-456",
  "items": ["Product A", "Product B"],
  "totalAmount": 299.99,
  "status": "PENDING",
  "timestamp": 1702742400000
}
```

**Response 400 Bad Request :**
```json
{
  "error": "Invalid order data",
  "message": "Total amount must be positive"
}
```

#### 2. Générer une Commande Aléatoire

**GET** `/api/orders/generate`

**Response 200 OK :**
```json
{
  "id": "ORDER-UUID-xyz",
  "customerId": "CUST-random",
  "items": ["Item1", "Item2"],
  "totalAmount": 499.99,
  "status": "PENDING",
  "timestamp": 1702742400000
}
```

#### 3. Health Check

**GET** `/actuator/health`

**Response 200 OK :**
```json
{
  "status": "UP"
}
```

---

## 🛠️ Commandes Utiles

### Gestion de Kafka

```bash
# Arrêter Kafka
docker-compose down

# Redémarrer Kafka
docker-compose restart

# Voir les logs Kafka
docker logs kafka-broker -f

# Voir les logs Zookeeper
docker logs zookeeper -f

# Lister les topics
docker exec -it kafka-broker kafka-topics --list --bootstrap-server localhost:9092

# Décrire un topic
docker exec -it kafka-broker kafka-topics --describe --topic orders-input --bootstrap-server localhost:9092
```

### Gestion du Projet

```bash
# Nettoyer et reconstruire
mvn clean package

# Compiler sans tests
mvn clean install -DskipTests

# Lancer en mode debug
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

---

## 🐛 Dépannage

### ❌ Kafka ne démarre pas

**Cause** : Docker Desktop non lancé ou ports occupés

**Solution** :
```bash
# Vérifier Docker
docker --version
docker ps

# Vérifier les ports 9092 et 2181
# Windows
netstat -ano | findstr :9092
netstat -ano | findstr :2181

# Linux/Mac
lsof -ti:9092
lsof -ti:2181

# Redémarrer Docker Desktop puis
docker-compose down
docker-compose up -d
```

### ❌ Erreur de connexion Kafka

**Cause** : Configuration incorrecte dans `application.yml`

**Solution** :
- Vérifier `bootstrap-servers: localhost:9092`
- Vérifier que Kafka tourne : `docker ps`
- Tester la connexion : `telnet localhost 9092`

### ❌ Topics non créés

**Cause** : docker-compose.yml mal configuré

**Solution** :
```bash
# Créer manuellement les topics
docker exec -it kafka-broker bash
kafka-topics --create --topic orders-input --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
kafka-topics --create --topic orders-processed --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
kafka-topics --create --topic orders-dlq --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
exit
```

### ❌ Erreur de sérialisation JSON

**Cause** : Trusted packages non configurés

**Solution** : Vérifier dans `application.yml` :
```yaml
spring.kafka.consumer.properties:
  spring.json.trusted.packages: "*"
```

### ❌ L'application ne démarre pas

**Cause** : Port 8080 déjà utilisé

**Solution** :
```bash
# Changer le port dans application.yml
server:
  port: 8081

# Ou tuer le processus sur le port 8080
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Linux/Mac
lsof -ti:8080 | xargs kill -9
```

---

## 🛡️ Technologies Utilisées

| Technologie | Version | Usage |
|------------|---------|-------|
| **Java** | 17 | Langage de développement |
| **Spring Boot** | 3.x | Framework principal |
| **Spring Kafka** | 3.x | Intégration Kafka |
| **Apache Kafka** | 3.5+ | Message broker |
| **Docker** | Latest | Conteneurisation Kafka |
| **Maven** | 3.9+ | Build tool |
| **JUnit 5** | 5.9+ | Tests unitaires |
| **Mockito** | 5.x | Mocking pour tests |
| **JaCoCo** | 0.8.10 | Couverture de code |
| **Jackson** | 2.15+ | Sérialisation JSON |

---

## 📚 Ressources

- [Spring Kafka Documentation](https://spring.io/projects/spring-kafka)
- [Apache Kafka Quick Start](https://kafka.apache.org/quickstart)
- [Baeldung Spring Kafka](https://www.baeldung.com/spring-kafka)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

---

## ✅ Checklist Finale

### Infrastructure (Chiva)
- [x] Docker Compose configuré
- [x] Kafka + Zookeeper démarrés
- [x] Topics créés automatiquement
- [x] application.yml configuré
- [x] Structure packages créée

### Producer (Zahra)
- [x] OrderProducerService implémenté
- [x] OrderController créé
- [x] Modèle Order défini
- [x] Endpoints REST fonctionnels

### Consumer (Wethigha + Emane)
- [x] OrderConsumerService implémenté
- [x] Logique de traitement ajoutée
- [x] Mécanisme de retry configuré
- [x] DLQ Consumer implémenté

### Tests & Documentation (Aya)
- [x] Tests unitaires écrits
- [x] Tests d'intégration Kafka
- [x] Documentation complète
- [x] README détaillé

**Projet complet et opérationnel ! 🎉**

---
