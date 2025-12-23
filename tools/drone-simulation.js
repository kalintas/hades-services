#!/usr/bin/env node

/**
 * Drone Simulation Script
 * 
 * Simulates drones uploading earthquake damage images to the Hades system.
 * Authenticates via Firebase Admin SDK using service account credentials.
 * 
 * Usage:
 *   node drone-simulation.js --earthquake-id <uuid>
 * 
 * Options:
 *   --earthquake-id  UUID of the earthquake to associate images with (required)
 *   --interval       Interval between uploads in ms (default: 2000)
 *   --help           Show this help message
 * 
 * Setup:
 *   Requires FIREBASE_CREDENTIALS (base64 encoded) in parent .env file
 */

import 'dotenv/config';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import FormData from 'form-data';
import fetch from 'node-fetch';
import admin from 'firebase-admin';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const API_URL = 'http://localhost:8080';
const DRONE_UID = 'drone-simulator';

// Load Firebase credentials from parent .env
function loadFirebaseCredentials() {
    // Try parent directory's .env
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

    // Fallback to environment variable
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

// Get Firebase ID token for drone authentication
async function getFirebaseIdToken(projectId) {
    try {
        // Create a custom token for the drone
        const customToken = await admin.auth().createCustomToken(DRONE_UID, {
            role: 'drone'
        });

        // Exchange custom token for ID token using Firebase REST API
        const apiKey = await getFirebaseApiKey(projectId);

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

// Get Firebase API key
function getFirebaseApiKey(projectId) {
    return 'AIzaSyAJJ15n-sfluuhAlGoqb6uUnxu0nEapvm4';
}

// Parse command line arguments
function parseArgs() {
    const args = process.argv.slice(2);
    const options = {
        earthquakeId: null,
        interval: 2000,
        help: false
    };

    for (let i = 0; i < args.length; i++) {
        switch (args[i]) {
            case '--earthquake-id':
                options.earthquakeId = args[++i];
                break;
            case '--interval':
                options.interval = parseInt(args[++i], 10);
                break;
            case '--help':
            case '-h':
                options.help = true;
                break;
        }
    }

    return options;
}

function showHelp() {
    console.log(`
Drone Simulation Script
=======================

Simulates drones uploading earthquake damage images to the Hades system.
Authenticates via Firebase Admin SDK.

Usage:
  node drone-simulation.js --earthquake-id <uuid>

Options:
  --earthquake-id  UUID of the earthquake to associate images with (required)
  --interval       Interval between uploads in ms (default: 2000)
  --help, -h       Show this help message

Setup:
  1. Ensure FIREBASE_CREDENTIALS (base64) and FIREBASE_API_KEY are in ../env
  2. Place earthquake images in ./earthquake-images/
  3. Run: npm install && node drone-simulation.js --earthquake-id <uuid>
`);
}

// Fetch active drones from the server
async function fetchActiveDrones(idToken) {
    const url = `${API_URL}/images/active-drones`;

    try {
        const response = await fetch(url, {
            headers: {
                'Cookie': `hades_session=${idToken}`
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${await response.text()}`);
        }

        const drones = await response.json();
        return drones;
    } catch (error) {
        throw new Error(`Failed to fetch active drones: ${error.message}`);
    }
}

// Get all image files from the earthquake-images directory
function getImageFiles() {
    const imagesDir = path.join(__dirname, 'earthquake-images');

    if (!fs.existsSync(imagesDir)) {
        console.error(`Error: Images directory not found at ${imagesDir}`);
        console.log('Please create the directory and add earthquake images.');
        process.exit(1);
    }

    const files = fs.readdirSync(imagesDir)
        .filter(file => /\.(jpg|jpeg|png|gif|webp)$/i.test(file))
        .map(file => path.join(imagesDir, file));

    if (files.length === 0) {
        console.error('Error: No image files found in earthquake-images directory.');
        console.log('Supported formats: jpg, jpeg, png, gif, webp');
        process.exit(1);
    }

    return files;
}

// Select a random drone from the list
function selectRandomDrone(drones) {
    const index = Math.floor(Math.random() * drones.length);
    return drones[index];
}

// Upload a single image
async function uploadImage(options, imagePath, drone, idToken) {
    const form = new FormData();
    const fileName = path.basename(imagePath);

    form.append('files', fs.createReadStream(imagePath), {
        filename: fileName,
        contentType: getContentType(imagePath)
    });
    form.append('earthquakeId', options.earthquakeId);
    form.append('droneId', drone.id);
    form.append('neighborhood', 'Unknown');

    const url = `${API_URL}/images/drone-upload`;

    try {
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Cookie': `hades_session=${idToken}`,
                ...form.getHeaders()
            },
            body: form
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`HTTP ${response.status}: ${errorText}`);
        }

        const result = await response.json();
        return result;
    } catch (error) {
        throw new Error(`Failed to upload ${fileName}: ${error.message}`);
    }
}

function getContentType(filePath) {
    const ext = path.extname(filePath).toLowerCase();
    const contentTypes = {
        '.jpg': 'image/jpeg',
        '.jpeg': 'image/jpeg',
        '.png': 'image/png',
        '.gif': 'image/gif',
        '.webp': 'image/webp'
    };
    return contentTypes[ext] || 'application/octet-stream';
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

// Main simulation function
async function runSimulation(options) {
    console.log('\n🚁 Drone Image Upload Simulation');
    console.log('================================\n');

    // Initialize Firebase and get token
    console.log('Initializing Firebase authentication...');
    const projectId = initializeFirebase();
    console.log(`Project: ${projectId}`);

    const idToken = await getFirebaseIdToken(projectId);
    console.log('✅ Firebase authentication successful\n');

    console.log(`Earthquake ID: ${options.earthquakeId}`);
    console.log(`API URL:       ${API_URL}`);
    console.log(`Interval:      ${options.interval}ms\n`);

    // Fetch active drones
    console.log('Fetching active drones from server...');
    const drones = await fetchActiveDrones(idToken);

    if (drones.length === 0) {
        console.error('Error: No active drones found in the system.');
        console.log('Please create at least one drone with ACTIVE status.');
        process.exit(1);
    }

    console.log(`Found ${drones.length} active drone(s):`);
    drones.forEach(d => console.log(`  - ${d.name} (${d.model}) [${d.id}]`));
    console.log('');

    const images = getImageFiles();
    console.log(`Found ${images.length} image(s) to upload.\n`);
    console.log('Starting upload simulation...\n');
    console.log('-'.repeat(50));

    let successCount = 0;
    let failCount = 0;

    for (let i = 0; i < images.length; i++) {
        const imagePath = images[i];
        const fileName = path.basename(imagePath);
        const progress = `[${i + 1}/${images.length}]`;

        // Randomly select a drone for this upload
        const drone = selectRandomDrone(drones);

        console.log(`\n${progress} Uploading: ${fileName}`);
        console.log(`   Drone: ${drone.name} (${drone.model})`);

        try {
            const result = await uploadImage(options, imagePath, drone, idToken);
            successCount++;
            console.log(`   ✅ Success! Image ID: ${result[0]?.id || 'unknown'}`);
        } catch (error) {
            failCount++;
            console.error(`   ❌ Failed: ${error.message}`);
        }

        // Wait before next upload (except for the last one)
        if (i < images.length - 1) {
            console.log(`   Waiting ${options.interval}ms before next upload...`);
            await sleep(options.interval);
        }
    }

    console.log('\n' + '-'.repeat(50));
    console.log('\n📊 Simulation Complete');
    console.log(`   ✅ Successful uploads: ${successCount}`);
    console.log(`   ❌ Failed uploads:     ${failCount}`);
    console.log(`   📁 Total images:       ${images.length}\n`);
}

// Entry point
async function main() {
    const options = parseArgs();

    if (options.help) {
        showHelp();
        process.exit(0);
    }

    if (!options.earthquakeId) {
        console.error('Error: Missing required argument: --earthquake-id');
        console.log('Use --help for usage information.');
        process.exit(1);
    }

    try {
        await runSimulation(options);
    } catch (error) {
        console.error(`Simulation error: ${error.message}`);
        process.exit(1);
    }
}

main();
