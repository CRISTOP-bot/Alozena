const { before, after, beforeEach, test } = require('node:test');
const assert = require('node:assert/strict');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { ref, uploadBytes, getBytes, deleteObject } = require('firebase/storage');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-alozena',
    storage: { rules: readFileSync(resolve(__dirname, '../../storage.rules'), 'utf8') }
  });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearStorage(); });

test('host can upload a bounded supported image only in own listing path', async () => {
  const owner = env.authenticatedContext('host-123').storage();
  await assertSucceeds(uploadBytes(ref(owner, 'listing-photos/host-123/stay-1/cover'), new Uint8Array([1, 2, 3]), { contentType: 'image/jpeg' }));
  await assertFails(uploadBytes(ref(owner, 'listing-photos/other/stay-2/cover'), new Uint8Array([1]), { contentType: 'image/jpeg' }));
  await assertFails(uploadBytes(ref(owner, 'listing-photos/host-123/stay-2/cover'), new Uint8Array([1]), { contentType: 'text/plain' }));
  await assertFails(uploadBytes(ref(owner, 'listing-photos/host-123/stay-3/cover'), new Uint8Array(10 * 1024 * 1024 + 1), { contentType: 'image/png' }));
  await assertSucceeds(getBytes(ref(owner, 'listing-photos/host-123/stay-1/cover')));
  await assertSucceeds(deleteObject(ref(owner, 'listing-photos/host-123/stay-1/cover')));
});

test('anonymous client cannot upload images', async () => {
  await assertFails(uploadBytes(ref(env.unauthenticatedContext().storage(), 'listing-photos/host-123/stay-1/cover'), new Uint8Array([1]), { contentType: 'image/jpeg' }));
});
