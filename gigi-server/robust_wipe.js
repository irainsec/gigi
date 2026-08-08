const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();
const auth = admin.auth();

async function deleteCollection(name) {
    console.log(`Attempting to delete collection: ${name}`);
    const snapshot = await db.collection(name).get();
    console.log(`Found ${snapshot.size} docs in ${name}`);
    const batch = db.batch();
    snapshot.docs.forEach(doc => batch.delete(doc.ref));
    await batch.commit();
    console.log(`Deleted all docs in ${name}`);
}

async function run() {
    try {
        await deleteCollection('users');
        await deleteCollection('connections');
        await deleteCollection('scribbles'); // Just in case
        await deleteCollection('actions');   // Just in case
        
        console.log('Fetching users to delete...');
        const listUsers = await auth.listUsers();
        console.log(`Found ${listUsers.users.length} users.`);
        for (const user of listUsers.users) {
            await auth.deleteUser(user.uid);
            console.log(`Deleted user: ${user.uid}`);
        }
        console.log('✅ COMPLETE');
    } catch (e) {
        console.error('❌ FAILED:', e);
    } finally {
        process.exit();
    }
}

run();
