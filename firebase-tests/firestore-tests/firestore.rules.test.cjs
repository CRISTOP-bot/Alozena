const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { before, after, beforeEach, test } = require('node:test');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, Timestamp, serverTimestamp, query, collection, where, getDocs } = require('firebase/firestore');

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-estadia',
    firestore: { rules: readFileSync(resolve(__dirname, '../../firestore.rules'), 'utf8') }
  });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });
async function seedStay(id = 'stay-1', extra = {}) {
  await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), 'stays', id), {
    title: 'Casa demo', location: 'Oaxaca, México', category: 'Casas', pricePerNight: 1200,
    maxGuests: 3, published: true, hostUid: 'host-1', ...extra
  }));
}

test('published listings are public; unpublished listings stay private', async () => {
  await seedStay(); await seedStay('private-stay', { published: false });
  await assertSucceeds(getDoc(doc(env.unauthenticatedContext().firestore(), 'stays/stay-1')));
  await assertFails(getDoc(doc(env.authenticatedContext('guest').firestore(), 'stays/private-stay')));
});

test('profiles and favorites are isolated to their owner', async () => {
  await seedStay();
  await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), 'users/owner/favorites/stay-1'), { stayId: 'stay-1' }));
  const guest = env.authenticatedContext('guest').firestore();
  await assertFails(getDoc(doc(guest, 'users/owner')));
  await assertFails(getDoc(doc(guest, 'users/owner/favorites/stay-1')));
  const owner = env.authenticatedContext('owner').firestore();
  await assertSucceeds(getDoc(doc(owner, 'users/owner/favorites/stay-1')));
  await assertFails(setDoc(doc(owner, 'users/owner/favorites/stay-1'), { stayId: 'stay-1', createdAt: serverTimestamp(), extra: true }));
  await assertSucceeds(setDoc(doc(owner, 'users/owner/favorites/stay-1'), { stayId: 'stay-1', createdAt: serverTimestamp() }));
  await assertFails(setDoc(doc(owner, 'users/owner/favorites/missing'), { stayId: 'missing', createdAt: serverTimestamp() }));
});

test('booking requests require auth, correct owner, valid range and capacity', async () => {
  await seedStay();
  const valid = { guestUid: 'guest', stayId: 'stay-1', checkIn: Timestamp.fromDate(new Date('2027-05-01T00:00:00Z')), checkOut: Timestamp.fromDate(new Date('2027-05-03T00:00:00Z')), guests: 2, status: 'requested' };
  await assertFails(setDoc(doc(env.unauthenticatedContext().firestore(), 'bookings/anon'), valid));
  const db = env.authenticatedContext('guest').firestore();
  await assertSucceeds(setDoc(doc(db, 'bookings/good'), valid));
  await assertFails(setDoc(doc(db, 'bookings/wrong-user'), { ...valid, guestUid: 'other' }));
  await assertFails(setDoc(doc(db, 'bookings/too-many'), { ...valid, guests: 4 }));
  await assertFails(setDoc(doc(db, 'bookings/reversed'), { ...valid, checkIn: '2027-05-04' }));
  await assertFails(setDoc(doc(db, 'bookings/not-timestamp'), { ...valid, checkIn: 'tomorrow' }));
});

test('invalid calendar timestamps and same-day checkout are rejected', async () => {
  await seedStay();
  const db = env.authenticatedContext('guest').firestore();
  const base = { guestUid: 'guest', stayId: 'stay-1', checkIn: Timestamp.fromDate(new Date('2027-05-01T00:00:00Z')), checkOut: Timestamp.fromDate(new Date('2027-05-03T00:00:00Z')), guests: 1, status: 'requested' };
  await assertFails(setDoc(doc(db, 'bookings/same-day'), { ...base, checkOut: base.checkIn }));
  await assertFails(setDoc(doc(db, 'bookings/non-date'), { ...base, checkIn: '2027-02-30' }));
});

test('guest can query only their own booking requests', async () => {
  await seedStay();
  await env.withSecurityRulesDisabled(async c => {
    await setDoc(doc(c.firestore(), 'bookings/g1'), { guestUid: 'guest', stayId: 'stay-1', status: 'requested' });
    await setDoc(doc(c.firestore(), 'bookings/g2'), { guestUid: 'other', stayId: 'stay-1', status: 'requested' });
  });
  const guestDb = env.authenticatedContext('guest').firestore();
  const own = await assertSucceeds(getDocs(query(collection(guestDb, 'bookings'), where('guestUid', '==', 'guest'))));
  if (own.size !== 1) throw new Error(`Expected one own booking, got ${own.size}`);
  await assertFails(getDocs(collection(guestDb, 'bookings')));
});

test('booking request is visible to guest and host only', async () => {
  await seedStay();
  await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), 'bookings/b1'), { guestUid: 'guest', stayId: 'stay-1', status: 'requested' }));
  await assertSucceeds(getDoc(doc(env.authenticatedContext('guest').firestore(), 'bookings/b1')));
  await assertSucceeds(getDoc(doc(env.authenticatedContext('host-1').firestore(), 'bookings/b1')));
  await assertFails(getDoc(doc(env.authenticatedContext('stranger').firestore(), 'bookings/b1')));
});

test('only owner can cancel a pending request and cannot edit other fields', async () => {
  await seedStay();
  await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), 'bookings/b1'), { guestUid: 'guest', stayId: 'stay-1', status: 'requested', guests: 1 }));
  const guest = env.authenticatedContext('guest').firestore();
  await assertSucceeds(updateDoc(doc(guest, 'bookings/b1'), { status: 'cancelled' }));
  await assertFails(updateDoc(doc(guest, 'bookings/b1'), { status: 'confirmed' }));
  await assertFails(updateDoc(doc(env.authenticatedContext('stranger').firestore(), 'bookings/b1'), { status: 'cancelled' }));
});
