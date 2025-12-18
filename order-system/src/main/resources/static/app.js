// =============================================
// 🚀 Groupe 6 - Spring Boot + Kafka
// Application JavaScript
// =============================================

// Configuration API
const API_BASE = '/api/orders';

// Stockage local des commandes
let orders = [];

// Éléments DOM
const orderForm = document.getElementById('orderForm');
const generateBtn = document.getElementById('generateBtn');
const ordersContainer = document.getElementById('ordersContainer');
const toast = document.getElementById('toast');

// Initialisation
document.addEventListener('DOMContentLoaded', () => {
    loadOrdersFromStorage();
    renderOrders();
    initNavigation();
});

// Event Listeners
orderForm.addEventListener('submit', handleSubmitOrder);
generateBtn.addEventListener('click', handleGenerateOrder);

// Navigation smooth scroll et active state
function initNavigation() {
    const navLinks = document.querySelectorAll('.nav-link');
    
    navLinks.forEach(link => {
        link.addEventListener('click', (e) => {
            navLinks.forEach(l => l.classList.remove('active'));
            link.classList.add('active');
        });
    });
    
    // Update active nav on scroll
    window.addEventListener('scroll', () => {
        const sections = document.querySelectorAll('section[id]');
        const scrollPos = window.scrollY + 100;
        
        sections.forEach(section => {
            const top = section.offsetTop;
            const height = section.offsetHeight;
            const id = section.getAttribute('id');
            
            if (scrollPos >= top && scrollPos < top + height) {
                navLinks.forEach(link => {
                    link.classList.remove('active');
                    if (link.getAttribute('href') === `#${id}`) {
                        link.classList.add('active');
                    }
                });
            }
        });
    });
}

// Soumettre une commande personnalisée
async function handleSubmitOrder(e) {
    e.preventDefault();
    
    const customerId = document.getElementById('customerId').value;
    const itemsInput = document.getElementById('items').value;
    const amount = parseFloat(document.getElementById('amount').value);
    
    // Validation côté client
    if (amount < 0.01 || amount > 10000) {
        showToast('❌ Le montant doit être entre 0.01€ et 10000€', 'error');
        return;
    }
    
    // Parser les items
    const items = itemsInput.split(',').map(item => item.trim()).filter(item => item);
    
    if (items.length === 0) {
        showToast('❌ Veuillez ajouter au moins un article', 'error');
        return;
    }
    
    // Créer l'objet commande
    const order = {
        id: generateUUID(),
        customerId: customerId,
        items: items,
        totalAmount: amount,
        status: 'PENDING',
        timestamp: Date.now()
    };
    
    try {
        // Envoyer à l'API
        const response = await fetch(API_BASE, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(order)
        });
        
        if (response.ok) {
            addOrder(order);
            showToast('✅ Commande envoyée vers Kafka!', 'success');
            orderForm.reset();
            simulateProcessing(order.id);
        } else {
            showToast('❌ Erreur lors de l\'envoi', 'error');
        }
    } catch (error) {
        console.error('Erreur:', error);
        // Ajouter quand même localement pour la démo
        addOrder(order);
        showToast('✅ Commande envoyée!', 'success');
        orderForm.reset();
        simulateProcessing(order.id);
    }
}

// Générer une commande aléatoire
async function handleGenerateOrder() {
    generateBtn.disabled = true;
    generateBtn.innerHTML = '<span class="loading"></span> Génération...';
    
    try {
        const response = await fetch(`${API_BASE}/generate`);
        
        if (response.ok) {
            // Créer une commande locale pour l'affichage
            const order = {
                id: generateUUID(),
                customerId: 'CUST-' + Math.floor(Math.random() * 1000),
                items: getRandomItems(),
                totalAmount: parseFloat((Math.random() * 500 + 10).toFixed(2)),
                status: 'PENDING',
                timestamp: Date.now()
            };
            
            addOrder(order);
            showToast('🎲 Commande générée et envoyée vers Kafka!', 'success');
            simulateProcessing(order.id);
        }
    } catch (error) {
        console.error('Erreur:', error);
        // Générer localement pour la démo
        const order = {
            id: generateUUID(),
            customerId: 'CUST-' + Math.floor(Math.random() * 1000),
            items: getRandomItems(),
            totalAmount: parseFloat((Math.random() * 500 + 10).toFixed(2)),
            status: 'PENDING',
            timestamp: Date.now()
        };
        
        addOrder(order);
        showToast('🎲 Commande générée!', 'success');
        simulateProcessing(order.id);
    } finally {
        generateBtn.disabled = false;
        generateBtn.innerHTML = '🎲 Générer Aléatoire';
    }
}

// Ajouter une commande
function addOrder(order) {
    orders.unshift(order);
    if (orders.length > 10) orders.pop();
    saveOrdersToStorage();
    renderOrders();
    
    // Scroll to orders section
    document.getElementById('ordersContainer').scrollIntoView({ behavior: 'smooth', block: 'center' });
}

// Simuler le traitement de la commande (comme le vrai backend)
function simulateProcessing(orderId) {
    // Étape 1: PROCESSING (après 1 seconde)
    setTimeout(() => {
        updateOrderStatus(orderId, 'PROCESSING');
        showToast('🔄 Commande en cours de traitement...', 'info');
    }, 1000);
    
    // Étape 2: COMPLETED ou FAILED (après 3 secondes)
    setTimeout(() => {
        // 90% de succès, 10% d'échec (comme dans le vrai code avec 10% stock indisponible)
        const success = Math.random() > 0.1;
        
        if (success) {
            updateOrderStatus(orderId, 'COMPLETED');
            showToast('✅ Commande traitée avec succès! → orders-processed', 'success');
        } else {
            updateOrderStatus(orderId, 'FAILED');
            showToast('⚠️ Erreur! Commande envoyée vers DLQ (orders-dlq)', 'error');
        }
    }, 3000);
}

// Mettre à jour le statut d'une commande
function updateOrderStatus(orderId, newStatus) {
    const order = orders.find(o => o.id === orderId);
    if (order) {
        order.status = newStatus;
        saveOrdersToStorage();
        renderOrders();
    }
}

// Rendre les commandes
function renderOrders() {
    if (orders.length === 0) {
        ordersContainer.innerHTML = '<p class="empty-message">Aucune commande pour le moment. Créez-en une via la démo ci-dessus !</p>';
        return;
    }
    
    ordersContainer.innerHTML = orders.map(order => `
        <div class="order-card">
            <div class="order-header">
                <span class="order-id">#${order.id.substring(0, 8)}...</span>
                <span class="order-status status-${order.status.toLowerCase()}">${getStatusEmoji(order.status)} ${order.status}</span>
            </div>
            <div class="order-details">
                <div class="order-detail">
                    <span class="order-detail-label">👤 Client:</span>
                    <span class="order-detail-value">${order.customerId}</span>
                </div>
                <div class="order-detail">
                    <span class="order-detail-label">💰 Montant:</span>
                    <span class="order-detail-value">${order.totalAmount.toFixed(2)}€</span>
                </div>
                <div class="order-detail">
                    <span class="order-detail-label">📦 Articles:</span>
                    <span class="order-detail-value">${order.items.join(', ')}</span>
                </div>
                <div class="order-detail">
                    <span class="order-detail-label">🕐 Heure:</span>
                    <span class="order-detail-value">${formatTime(order.timestamp)}</span>
                </div>
            </div>
        </div>
    `).join('');
}

// Obtenir l'emoji du statut
function getStatusEmoji(status) {
    switch(status) {
        case 'PENDING': return '⏳';
        case 'PROCESSING': return '🔄';
        case 'COMPLETED': return '✅';
        case 'FAILED': return '❌';
        default: return '❓';
    }
}

// Formater l'heure
function formatTime(timestamp) {
    const date = new Date(timestamp);
    return date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

// Générer un UUID
function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

// Obtenir des items aléatoires
function getRandomItems() {
    const allItems = [
        'Laptop', 'Souris', 'Clavier', 'Écran 27"', 'Casque Audio', 
        'Webcam HD', 'USB Hub', 'SSD 1TB', 'RAM 16GB', 'Câble HDMI',
        'Imprimante', 'Scanner', 'Tablette', 'Smartphone', 'Chargeur'
    ];
    const count = Math.floor(Math.random() * 3) + 1;
    const items = [];
    for (let i = 0; i < count; i++) {
        const item = allItems[Math.floor(Math.random() * allItems.length)];
        if (!items.includes(item)) items.push(item);
    }
    return items;
}

// Afficher un toast
function showToast(message, type = 'info') {
    toast.textContent = message;
    toast.className = `toast ${type} show`;
    
    setTimeout(() => {
        toast.classList.remove('show');
    }, 4000);
}

// Sauvegarder dans localStorage
function saveOrdersToStorage() {
    localStorage.setItem('groupe6-kafka-orders', JSON.stringify(orders));
}

// Charger depuis localStorage
function loadOrdersFromStorage() {
    const saved = localStorage.getItem('groupe6-kafka-orders');
    if (saved) {
        orders = JSON.parse(saved);
    }
}
