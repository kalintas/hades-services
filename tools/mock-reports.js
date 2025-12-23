#!/usr/bin/env node

/**
 * Mock Reports Generation Script
 * 
 * Generates mock damage assessment reports for drone images that don't have reports yet.
 * This simulates what a VQA/VLM model would produce for earthquake damage analysis.
 * 
 * Usage:
 *   node mock-reports.js
 * 
 * Setup:
 *   Requires FIREBASE_CREDENTIALS (base64 encoded) in parent .env file
 */

import 'dotenv/config';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import fetch from 'node-fetch';
import admin from 'firebase-admin';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const API_URL = 'http://localhost:8080';
const DRONE_UID = 'mock-reports-generator';

// Turkish locations for mock data
const LOCATIONS = [
    'Merkez', 'Sahil Bölgesi', 'Konut Alanı', 'Sanayi Bölgesi',
    'Liman Alanı', 'Ticaret Merkezi', 'Tarihi Bölge', 'Hastane Çevresi',
    'Üniversite Kampüsü', 'Toplu Konut Alanı', 'Kıyı Şeridi', 'Yerleşim Alanı'
];

// Report templates in Turkish
const REPORT_TEMPLATES = [
    (collapsed, damaged, blocked, location) =>
        `${location} bölgesinde ${collapsed > 0 ? 'ciddi' : 'orta düzeyde'} hasar tespit edilmiştir. ${collapsed} bina tamamen çökmüş, ${damaged} yapıda yapısal hasar gözlemlenmiştir. ${blocked > 0 ? `${blocked} yol enkaz nedeniyle kapalıdır.` : 'Yollar geçişe açıktır.'} Bölgede arama kurtarma çalışmaları ${collapsed > 0 ? 'acil olarak' : ''} başlatılmalıdır.`,

    (collapsed, damaged, blocked, location) =>
        `${location} alanında deprem sonrası değerlendirme tamamlanmıştır. Tespit edilen hasarlar: ${collapsed} çökmüş yapı, ${damaged} hasarlı bina, ${blocked} tıkalı yol. ${collapsed > 2 ? 'Bölge yüksek risk altındadır, acil tahliye önerilir.' : 'Kontrollü giriş yapılabilir.'}`,

    (collapsed, damaged, blocked, location) =>
        `Drone görüntüsü analizi sonucu ${location} bölgesinde ${damaged + collapsed} yapıda hasar tespit edilmiştir. ${collapsed > 0 ? `Bunlardan ${collapsed} tanesi tamamen yıkılmıştır.` : 'Tam yıkım gözlemlenmemiştir.'} ${blocked > 0 ? `Ulaşım ${blocked} noktada engellenmiştir.` : 'Ulaşım ağı sağlamdır.'}`,
];

// Load Firebase credentials from parent .env
function loadFirebaseCredentials() {
    const parentEnvPath = path.join(__dirname, '..', '.env');

    if (fs.existsSync(parentEnvPath)) {
        const envContent = fs.readFileSync(parentEnvPath, 'utf-8');
        const match = envContent.match(/FIREBASE_CREDENTIALS=(.+)/);
        if (match) {
            const base64Creds = match[1].trim();
            const jsonString = Buffer.from(base64Creds, 'base64').toString('utf-8');
            return JSON.parse(jsonString);
        }
    }

    if (process.env.FIREBASE_CREDENTIALS) {
        const jsonString = Buffer.from(process.env.FIREBASE_CREDENTIALS, 'base64').toString('utf-8');
        return JSON.parse(jsonString);
    }

    throw new Error('FIREBASE_CREDENTIALS not found. Check parent .env file or environment.');
}

// Initialize Firebase Admin SDK
function initializeFirebase() {
    const credentials = loadFirebaseCredentials();

    if (!admin.apps.length) {
        admin.initializeApp({
            credential: admin.credential.cert(credentials)
        });
    }

    return credentials.project_id;
}

// Get Firebase ID token
async function getFirebaseIdToken(projectId) {
    try {
        const customToken = await admin.auth().createCustomToken(DRONE_UID, {
            role: 'system'
        });

        const apiKey = 'AIzaSyAJJ15n-sfluuhAlGoqb6uUnxu0nEapvm4';

        const response = await fetch(
            `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${apiKey}`,
            {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    token: customToken,
                    returnSecureToken: true
                })
            }
        );

        if (!response.ok) {
            const error = await response.json();
            throw new Error(`Firebase auth error: ${JSON.stringify(error)}`);
        }

        const data = await response.json();
        return data.idToken;
    } catch (error) {
        throw new Error(`Failed to get Firebase token: ${error.message}`);
    }
}

// Fetch pending images (images without reports)
async function fetchPendingImages(idToken) {
    const url = `${API_URL}/reports/pending-images`;

    try {
        const response = await fetch(url, {
            headers: {
                'Cookie': `hades_session=${idToken}`
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${await response.text()}`);
        }

        return await response.json();
    } catch (error) {
        throw new Error(`Failed to fetch pending images: ${error.message}`);
    }
}

// Generate random analysis values
function generateAnalysis() {
    const collapsed = Math.floor(Math.random() * 5);  // 0-4
    const damaged = Math.floor(Math.random() * 15) + collapsed;  // collapsed to collapsed+15
    const blocked = Math.floor(Math.random() * 4);  // 0-3

    // Severity score based on damage (1-10 scale)
    const severityScore = Math.min(10, Math.round(
        (collapsed * 1.5 + damaged * 0.3 + blocked * 0.5 + Math.random() * 2) * 10
    ) / 10);

    return { collapsed, damaged, blocked, severityScore };
}

// Create a report for an image
async function createReport(image, idToken) {
    const { collapsed, damaged, blocked, severityScore } = generateAnalysis();
    const location = LOCATIONS[Math.floor(Math.random() * LOCATIONS.length)];
    const template = REPORT_TEMPLATES[Math.floor(Math.random() * REPORT_TEMPLATES.length)];

    const reportData = {
        droneImageId: image.id,
        title: `${image.earthquakeName || 'Deprem'} Hasar Raporu - ${image.fileName}`,
        location: location,
        report: template(collapsed, damaged, blocked, location),
        collapsedBuildings: collapsed,
        damagedStructures: damaged,
        blockedRoads: blocked,
        severityScore: severityScore
    };

    const url = `${API_URL}/reports`;

    try {
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Cookie': `hades_session=${idToken}`
            },
            body: JSON.stringify(reportData)
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`HTTP ${response.status}: ${errorText}`);
        }

        return await response.json();
    } catch (error) {
        throw new Error(`Failed to create report: ${error.message}`);
    }
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

// Main function
async function main() {
    console.log('\n📊 Mock Reports Generation Script');
    console.log('==================================\n');

    // Initialize Firebase and get token
    console.log('Initializing Firebase authentication...');
    const projectId = initializeFirebase();
    console.log(`Project: ${projectId}`);

    const idToken = await getFirebaseIdToken(projectId);
    console.log('✅ Firebase authentication successful\n');

    // Fetch pending images
    console.log('Fetching images without reports...');
    const pendingImages = await fetchPendingImages(idToken);

    if (pendingImages.length === 0) {
        console.log('✅ No pending images found. All images already have reports.');
        return;
    }

    console.log(`Found ${pendingImages.length} image(s) without reports.\n`);
    console.log('Creating reports...\n');
    console.log('-'.repeat(50));

    let successCount = 0;
    let failCount = 0;

    for (let i = 0; i < pendingImages.length; i++) {
        const image = pendingImages[i];
        const progress = `[${i + 1}/${pendingImages.length}]`;

        console.log(`\n${progress} Processing: ${image.fileName}`);
        console.log(`   Earthquake: ${image.earthquakeName || 'Unknown'}`);
        console.log(`   Drone: ${image.droneName || 'Unknown'}`);

        try {
            const report = await createReport(image, idToken);
            successCount++;
            console.log(`   ✅ Report created! Severity: ${report.severityScore}/10`);
            console.log(`      Collapsed: ${report.collapsedBuildings}, Damaged: ${report.damagedStructures}, Blocked: ${report.blockedRoads}`);
        } catch (error) {
            failCount++;
            console.error(`   ❌ Failed: ${error.message}`);
        }

        // Small delay between requests
        if (i < pendingImages.length - 1) {
            await sleep(200);
        }
    }

    console.log('\n' + '-'.repeat(50));
    console.log('\n📊 Generation Complete');
    console.log(`   ✅ Successful: ${successCount}`);
    console.log(`   ❌ Failed:     ${failCount}`);
    console.log(`   📁 Total:      ${pendingImages.length}\n`);
}

main().catch(error => {
    console.error(`Error: ${error.message}`);
    process.exit(1);
});
