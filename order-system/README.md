# 🚀 Système de Traitement de Commandes - Kafka

Projet Spring Boot + Apache Kafka pour le traitement de commandes en temps réel.

## 👥 Équipe - Groupe 6
- **Chiva** - Infrastructure Kafka
- **Zahra** - Producer
- **Wethigha** - Consumer basique
- **Emane** - Traitement + Erreurs + Retry
- **Aya** - Tests + Documentation

---

## 📋 Prérequis

- Java 17
- Docker Desktop
- Maven
- Git

---

## 🔧 Installation et Démarrage

### 1️⃣ Cloner le projet
```bash
git clone <URL_du_repo>
cd order-system
```

### 2️⃣ Démarrer Kafka avec Docker
```bash
docker-compose up -d
```

Vérifier que les conteneurs tournent :
```bash
docker ps
```

Vous devriez voir `kafka-broker` et `zookeeper` en cours d'exécution.

### 3️⃣ Vérifier les topics Kafka
Les 3 topics sont créés automatiquement :
- `orders-input` - Pour recevoir les commandes
- `orders-processed` - Pour les commandes traitées
- `orders-dlq` - Dead Letter Queue pour les erreurs

Pour vérifier :
```bash
docker exec -it kafka-broker bash
kafka-topics --list --bootstrap-server localhost:9092
exit
```

### 4️⃣ Compiler le projet
```bash
mvn clean install
```

### 5️⃣ Lancer l'application
```bash
mvn spring-boot:run
```

L'application démarre sur `http://localhost:8080`

---

## 📂 Structure du Projet
```
order-system/
├── src/main/java/com/kafka/groupe6/order_system/
│   ├── config/          # Configurations Kafka
│   ├── model/           # Modèles de données (Order)
│   ├── producer/        # Services Producer
│   ├── consumer/        # Services Consumer
│   └── OrderSystemApplication.java
├── src/main/resources/
│   └── application.yml  # Configuration Spring Boot
├── docker-compose.yml   # Configuration Kafka
└── pom.xml
```

---

## 🔗 Topics Kafka

| Topic | Description | Responsable |
|-------|-------------|-------------|
| `orders-input` | Réception des nouvelles commandes | Zahra (Producer) |
| `orders-processed` | Commandes traitées avec succès | Emane (Consumer) |
| `orders-dlq` | Commandes en erreur | Emane (DLQ) |

---

## 🛠️ Commandes Utiles

### Arrêter Kafka
```bash
docker-compose down
```

### Redémarrer Kafka
```bash
docker-compose restart
```

### Voir les logs Kafka
```bash
docker logs kafka-broker -f
```

### Nettoyer et reconstruire
```bash
mvn clean package
```

---

## 🧪 Tests

Les tests seront implémentés par Aya.
```bash
mvn test
```

---

## 📚 Ressources

- [Spring Kafka Documentation](https://spring.io/projects/spring-kafka)
- [Apache Kafka Quick Start](https://kafka.apache.org/quickstart)
- [Baeldung Spring Kafka](https://www.baeldung.com/spring-kafka)

---

## 🐛 Dépannage

### Kafka ne démarre pas
- Vérifier que Docker Desktop est lancé
- Vérifier les ports 9092 et 2181 ne sont pas utilisés

### Erreur de connexion
- Vérifier `application.yml` : `bootstrap-servers: localhost:9092`

---

## ✅ Checklist Infrastructure (Chiva)

- [x] Docker Compose configuré
- [x] Kafka + Zookeeper démarrés
- [x] Topics créés (orders-input, orders-processed, orders-dlq)
- [x] application.yml configuré
- [x] Structure packages créée
- [x] README complété

**Infrastructure prête pour l'équipe ! 🎉**