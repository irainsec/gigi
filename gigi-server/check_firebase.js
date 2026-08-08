const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const auth = admin.auth();
const db = admin.firestore();

async function check() {
    try {
        console.log('Project ID:', serviceAccount.project_id);
        const listUsersResult = await auth.listUsers(10);
        console.log(`Successfully reached Auth. Found ${listUsersResult.users.length} users (limited to 10 for check).`);
        
        const collections = await db.listCollections();
        console.log('Collections in Firestore:', collections.map(c => c.id));
        
        if (collections.length === 0) {
            console.log('⚠️ No collections found in Firestore (default database).');
        }
    } catch (e) {
        console.error('❌ Connectivity check failed:', e);
    } finally {
        process.exit();
    }
}

check();
