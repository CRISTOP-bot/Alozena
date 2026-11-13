const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { before, after, beforeEach, test } = require('node:test');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, Timestamp, serverTimestamp } = require('firebase/firestore');

let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-estadia', firestore: { rules: readFileSync(resolve(__dirname, '../../firestore.rules'), 'utf8') } });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });
async function seedStay(id = 'stay-1', extra = {}) {
  await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), 'stays', id), {
    title: 'Casa demo', location: 'Oaxaca, México', category: 'Casas', pricePerNight: 1200,
    maxGuests: 3, published: true, hostUid: 'host-1', ...extra
  }));
}
async function seedBooking(id, guestUid = 'guest') {
  await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), 'bookings', id), {
    guestUid, stayId: 'stay-1', checkIn: Timestamp.fromDate(new Date('2027-05-01T00:00:00Z')),
    checkOut: Timestamp.fromDate(new Date('2027-05-03T00:00:00Z')), guests: 1, status: 'requested',
    createdAt: Timestamp.fromDate(new Date('2027-01-01T00:00:00Z'))
  }));
}

test('published listings are public; unpublished listings stay private', async () => {
  await seedStay(); await seedStay('private-stay', { published: false });
  await assertSucceeds(getDoc(doc(env.unauthenticatedContext().firestore(), 'stays/stay-1')));
  await assertFails(getDoc(doc(env.authenticatedContext('guest').firestore(), 'stays/private-stay')));
});

test('profiles and favorites are isolated to their owner', async () => {
  await seedStay();
  await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), 'users/owner/favorites/stay-1'), { stayId: 'stay-1', createdAt: Timestamp.now() }));
  const guest = env.authenticatedContext('guest').firestore();
  await assertFails(getDoc(doc(guest, 'users/owner')));
  await assertFails(getDoc(doc(guest, 'users/owner/favorites/stay-1')));
  const owner = env.authenticatedContext('owner').firestore();
  await assertSucceeds(getDoc(doc(owner, 'users/owner/favorites/stay-1')));
  await assertFails(setDoc(doc(owner, 'users/owner/favorites/stay-1'), { stayId: 'stay-1', createdAt: serverTimestamp(), extra: true }));
  await assertFails(setDoc(doc(owner, 'users/owner/favorites/stay-1'), { stayId: 'stay-1', createdAt: serverTimestamp() }));
  await assertFails(setDoc(doc(owner, 'users/owner/favorites/missing'), { stayId: 'missing', createdAt: serverTimestamp() }));
});

test('booking requests require auth, correct owner, valid range and capacity', async () => {
  await seedStay();
  const valid = { guestUid: 'guest', stayId: 'stay-1', checkIn: Timestamp.fromDate(new Date('2027-05-01T00:00:00Z')), checkOut: Timestamp.fromDate(new Date('2027-05-03T00:00:00Z')), guests: 2, status: 'requested', createdAt: serverTimestamp() };
  await assertFails(setDoc(doc(env.unauthenticatedContext().firestore(), 'bookings/anon'), valid));
  const db = env.authenticatedContext('guest').firestore();
  await assertSucceeds(setDoc(doc(db, 'bookings/good'), valid));
  await assertFails(setDoc(doc(db, 'bookings/wrong-user'), { ...valid, guestUid: 'other' }));
  await assertFails(setDoc(doc(db, 'bookings/too-many'), { ...valid, guests: 4 }));
  await assertFails(setDoc(doc(db, 'bookings/reversed'), { ...valid, checkIn: Timestamp.fromDate(new Date('2027-05-04T00:00:00Z')) }));
  await assertFails(setDoc(doc(db, 'bookings/not-timestamp'), { ...valid, checkIn: 'tomorrow' }));
});

test('invalid date values and same-day checkout are rejected', async () => {
  await seedStay();
  const db = env.authenticatedContext('guest').firestore();
  const base = { guestUid: 'guest', stayId: 'stay-1', checkIn: Timestamp.fromDate(new Date('2027-05-01T00:00:00Z')), checkOut: Timestamp.fromDate(new Date('2027-05-03T00:00:00Z')), guests: 1, status: 'requested', createdAt: serverTimestamp() };
  await assertFails(setDoc(doc(db, 'bookings/same-day'), { ...base, checkOut: base.checkIn }));
  await assertFails(setDoc(doc(db, 'bookings/non-date'), { ...base, checkIn: '2027-02-30' }));
});

test('booking get is visible to guest and host only', async () => {
  await seedStay(); await seedBooking('b1');
  await assertSucceeds(getDoc(doc(env.authenticatedContext('guest').firestore(), 'bookings/b1')));
  await assertSucceeds(getDoc(doc(env.authenticatedContext('host-1').firestore(), 'bookings/b1')));
  await assertFails(getDoc(doc(env.authenticatedContext('stranger').firestore(), 'bookings/b1')));
});

test('a guest can list only documents belonging to their own UID', async () => {
  await seedStay(); await seedBooking('g1', 'guest'); await seedBooking('g2', 'other');
  await assertSucceeds(getDoc(doc(env.authenticatedContext('guest').firestore(), 'bookings/g1')));
  await assertFails(getDoc(doc(env.authenticatedContext('guest').firestore(), 'bookings/g2')));
});

test('only owner can cancel pending request and cannot edit other fields', async () => {
  await seedStay(); await seedBooking('b1', 'guest');
  const guest = env.authenticatedContext('guest').firestore();
  await assertSucceeds(updateDoc(doc(guest, 'bookings/b1'), { status: 'cancelled' }));
  await assertFails(updateDoc(doc(guest, 'bookings/b1'), { status: 'confirmed' }));
  await assertFails(updateDoc(doc(env.authenticatedContext('stranger').firestore(), 'bookings/b1'), { status: 'cancelled' }));
});
