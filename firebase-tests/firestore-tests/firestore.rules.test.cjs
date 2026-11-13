const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { before, after, beforeEach, test } = require('node:test');
const assert = require('node:assert/strict');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, Timestamp, serverTimestamp, query, collection, where, getDocs } = require('firebase/firestore');

let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-alozena', firestore: { rules: readFileSync(resolve(__dirname, '../../firestore.rules'), 'utf8') } });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });
function listing(id = 'stay-1', hostUid = 'host-1') {
  return {
    title: 'Casa real publicada', location: 'Oaxaca, México', category: 'Casa', pricePerNight: 1200, cleaningFee: 0, securityDeposit: 0,
    maxGuests: 3, beds: 2, description: 'Alojamiento real descrito por su anfitrión.',
    coverImageUrl: 'https://photos.example.test/host-owned.jpg', searchTokens: ['casa', 'oaxaca'], source: 'host_submitted',
    hostUid, hostDisplayName: 'Anfitrión de prueba', published: true, moderationStatus: 'host_attested', createdAt: serverTimestamp(), reviewCount: 0
  };
}
async function seedStay(id = 'stay-1', extra = {}) {
  await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), 'stays', id), {
    ...listing(id), createdAt: Timestamp.fromDate(new Date('2027-01-01T00:00:00Z')), ...extra
  }));
}
async function seedBooking(id, guestUid = 'guest') {
  await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), 'bookings', id), {
    guestUid, stayId: 'stay-1', hostUid: 'host-1', stayTitle: 'Casa real publicada', nightlyPrice: 1200, cleaningFee: 0, checkIn: Timestamp.fromDate(new Date('2027-05-01T00:00:00Z')),
    checkOut: Timestamp.fromDate(new Date('2027-05-03T00:00:00Z')), guests: 1, status: 'requested',
    createdAt: Timestamp.fromDate(new Date('2027-01-01T00:00:00Z'))
  }));
}

test('only authenticated host can publish a real listing in own name', async () => {
  const guest = env.authenticatedContext('host-1').firestore();
  await assertSucceeds(setDoc(doc(guest, 'stays/stay-1'), listing()));
  await assertFails(setDoc(doc(guest, 'stays/another-host'), listing('another-host', 'someone-else')));
  await assertFails(setDoc(doc(env.unauthenticatedContext().firestore(), 'stays/anon'), listing('anon')));
  await assertFails(setDoc(doc(guest, 'stays/no-photo'), { ...listing('no-photo'), coverImageUrl: 'http://not-https.invalid/photo.jpg' }));
});

test('published listings are public; unpublished listings stay private', async () => {
  await seedStay(); await seedStay('private-stay', { published: false });
  await assertSucceeds(getDoc(doc(env.unauthenticatedContext().firestore(), 'stays/stay-1')));
  await assertFails(getDoc(doc(env.authenticatedContext('guest').firestore(), 'stays/private-stay')));
});

test('a host cannot change publication status or rating fields from the app', async () => {
  await seedStay();
  const host = env.authenticatedContext('host-1').firestore();
  await assertSucceeds(updateDoc(doc(host, 'stays/stay-1'), { title: 'Casa real editada' }));
  await assertFails(updateDoc(doc(host, 'stays/stay-1'), { published: false }));
  await assertFails(updateDoc(doc(host, 'stays/stay-1'), { rating: 5, reviewCount: 1 }));
  await assertFails(updateDoc(doc(env.authenticatedContext('stranger').firestore(), 'stays/stay-1'), { title: 'Cambio ajeno' }));
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
  const valid = { guestUid: 'guest', stayId: 'stay-1', hostUid: 'host-1', stayTitle: 'Casa real publicada', nightlyPrice: 1200, cleaningFee: 0, checkIn: Timestamp.fromDate(new Date('2027-05-01T00:00:00Z')), checkOut: Timestamp.fromDate(new Date('2027-05-03T00:00:00Z')), guests: 2, status: 'requested', createdAt: serverTimestamp() };
  await assertFails(setDoc(doc(env.unauthenticatedContext().firestore(), 'bookings/anon'), valid));
  const db = env.authenticatedContext('guest').firestore();
  await assertSucceeds(setDoc(doc(db, 'bookings/good'), valid));
  await assertFails(setDoc(doc(db, 'bookings/wrong-user'), { ...valid, guestUid: 'other' }));
  await assertFails(setDoc(doc(db, 'bookings/too-many'), { ...valid, guests: 4 }));
  await assertFails(setDoc(doc(db, 'bookings/reversed'), { ...valid, checkIn: Timestamp.fromDate(new Date('2027-05-04T00:00:00Z')) }));
  await assertFails(setDoc(doc(db, 'bookings/not-timestamp'), { ...valid, checkIn: 'tomorrow' }));
});

test('guest can read own requests and host can read requests for own listing', async () => {
  await seedStay(); await seedBooking('b1');
  await assertSucceeds(getDoc(doc(env.authenticatedContext('guest').firestore(), 'bookings/b1')));
  await assertSucceeds(getDoc(doc(env.authenticatedContext('host-1').firestore(), 'bookings/b1')));
  await assertFails(getDoc(doc(env.authenticatedContext('stranger').firestore(), 'bookings/b1')));
  const hostBookings = await assertSucceeds(getDocs(query(collection(env.authenticatedContext('host-1').firestore(), 'bookings'), where('hostUid', '==', 'host-1'))));
  assert.equal(hostBookings.size, 1);
});

test('only owner can cancel pending request and cannot edit other fields', async () => {
  await seedStay(); await seedBooking('b1', 'guest');
  const guest = env.authenticatedContext('guest').firestore();
  await assertSucceeds(updateDoc(doc(guest, 'bookings/b1'), { status: 'cancelled' }));
  await assertFails(updateDoc(doc(guest, 'bookings/b1'), { status: 'confirmed' }));
  await assertFails(updateDoc(doc(env.authenticatedContext('stranger').firestore(), 'bookings/b1'), { status: 'cancelled' }));
});

test('only the host can confirm or decline a pending request', async () => {
  await seedStay(); await seedBooking('host-response', 'guest');
  const host = env.authenticatedContext('host-1').firestore();
  await assertSucceeds(updateDoc(doc(host, 'bookings/host-response'), { status: 'accepted', hostRespondedAt: serverTimestamp() }));
  await seedBooking('host-response-2', 'guest');
  await assertFails(updateDoc(doc(env.authenticatedContext('stranger').firestore(), 'bookings/host-response-2'), { status: 'declined', hostRespondedAt: serverTimestamp() }));
});
