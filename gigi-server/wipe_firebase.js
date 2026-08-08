const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();
const auth = admin.auth();

async function deleteCollection(collectionPath, batchSize) {
  const collectionRef = db.collection(collectionPath);
  const query = collectionRef.orderBy('__name__').limit(batchSize);

  return new Promise((resolve, reject) => {
    deleteQueryBatch(db, query, resolve).catch(reject);
  });
}

async function deleteQueryBatch(db, query, resolve) {
  const snapshot = await query.get();

  const batchSize = snapshot.size;
  if (batchSize === 0) {
    resolve();
    return;
  }

  const batch = db.batch();
  snapshot.docs.forEach((doc) => {
    batch.delete(doc.ref);
  });
  await batch.commit();

  process.nextTick(() => {
    deleteQueryBatch(db, query, resolve);
  });
}

async function deleteAllUsers() {
    let nextPageToken;
    do {
        const listUsersResult = await auth.listUsers(1000, nextPageToken);
        const uids = listUsersResult.users.map(user => user.uid);
        if (uids.length > 0) {
            await auth.deleteUsers(uids);
            console.log(`Deleted ${uids.length} users`);
        }
        nextPageToken = listUsersResult.pageToken;
    } while (nextPageToken);
}

async function wipe() {
  console.log('🚀 Starting Firebase Wipe...');
  
  try {
    console.log('--- Wiping Firestore ---');
    const usersSnap = await db.collection('users').get();
    console.log(`Found ${usersSnap.size} documents in "users" collection.`);
    await deleteCollection('users', 100);
    console.log('✅ "users" collection cleared.');
    
    const connSnap = await db.collection('connections').get();
    console.log(`Found ${connSnap.size} documents in "connections" collection.`);
    await deleteCollection('connections', 100);
    console.log('✅ "connections" collection cleared.');

    console.log('--- Wiping Auth ---');
    await deleteAllUsers();
    console.log('✅ All Auth users cleared.');

    console.log('✨ EVERYTHING WIPED SUCCESSFULLY!');
  } catch (error) {
    console.error('❌ FATAL ERROR DURING WIPE:', error);
  } finally {
    process.exit();
  }
}

wipe();
