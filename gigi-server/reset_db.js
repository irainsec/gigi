const mongoose = require('mongoose');
const fs = require('fs');
const path = require('path');

const MONGO_BASE_URL = process.env.MONGO_BASE_URL || 'mongodb://localhost:6970';
const CAPTURES_DIR = path.join(__dirname, 'captures');

async function resetEverything() {
    console.log('🚀 Starting System Reset...');

    try {
        // 1. Connect to MongoDB to find all databases
        const adminConn = await mongoose.createConnection(`${MONGO_BASE_URL}/admin`).asPromise();
        const admin = adminConn.db.admin();
        const dbs = await admin.listDatabases();

        const targetDbs = dbs.databases
            .map(db => db.name)
            .filter(name => name.startsWith('session_') || name === 'gigi' || name === 'screensaver');

        console.log(`Found ${targetDbs.length} databases to drop:`, targetDbs);

        for (const dbName of targetDbs) {
            const conn = await mongoose.createConnection(`${MONGO_BASE_URL}/${dbName}`).asPromise();
            await conn.dropDatabase();
            console.log(`✅ Dropped database: ${dbName}`);
            await conn.close();
        }
        await adminConn.close();

        // 2. Clear Captures Directory
        if (fs.existsSync(CAPTURES_DIR)) {
            const clearDir = (dir) => {
                const files = fs.readdirSync(dir);
                for (const file of files) {
                    const fullPath = path.join(dir, file);
                    if (fs.statSync(fullPath).isDirectory()) {
                        clearDir(fullPath);
                        fs.rmdirSync(fullPath);
                    } else {
                        fs.unlinkSync(fullPath);
                    }
                }
            };
            clearDir(CAPTURES_DIR);
            console.log('✅ Cleared captures directory.');
        }

        console.log('\n✨ Reset Complete! You can now restart your server.');
        process.exit(0);
    } catch (err) {
        console.error('❌ Reset Failed:', err);
        process.exit(1);
    }
}

resetEverything();
