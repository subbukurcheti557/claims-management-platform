import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../config/api-config';
import { ClaimDocument } from '../models/api.models';
import { DocumentsStore, checkDocumentFile, formatBytes } from './documents.store';

const API = 'http://gw.test';

function file(name: string, type: string, size = 1000): File {
  return new File([new Uint8Array(size)], name, { type });
}

const doc = (id: string, patch: Partial<ClaimDocument> = {}): ClaimDocument => ({
  id,
  category: 'DAMAGE_PHOTO',
  categoryLabel: 'Damage photo',
  fileName: `${id}.jpg`,
  contentType: 'image/jpeg',
  sizeBytes: 1000,
  uploadedByName: 'Alice Tan',
  uploadedByRole: 'CLAIMANT',
  uploadedAt: '2026-11-16T10:00:00Z',
  contentUrl: `/api/claims/c-1/documents/${id}/content`,
  ...patch,
});

describe('checkDocumentFile', () => {
  it('accepts photos and PDFs within the limit', () => {
    expect(checkDocumentFile(file('a.jpg', 'image/jpeg'), 'DAMAGE_PHOTO')).toBeNull();
    expect(checkDocumentFile(file('a.webp', 'image/webp'), 'DAMAGE_PHOTO')).toBeNull();
    expect(checkDocumentFile(file('report.pdf', 'application/pdf'), 'POLICE_REPORT')).toBeNull();
  });

  it('explains what is wrong', () => {
    expect(checkDocumentFile(file('scan.pdf', 'application/pdf'), 'DAMAGE_PHOTO')).toContain('not a photo');
    expect(checkDocumentFile(file('notes.docx', 'application/msword'), 'OTHER')).toContain('not a supported file');
    expect(checkDocumentFile(file('empty.jpg', 'image/jpeg', 0), 'DAMAGE_PHOTO')).toContain('empty');
    expect(checkDocumentFile(file('big.jpg', 'image/jpeg', 10 * 1024 * 1024 + 1), 'DAMAGE_PHOTO')).toContain('10 MB');
  });

  it('formats sizes for people', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2 KB');
    expect(formatBytes(3.5 * 1024 * 1024)).toBe('3.5 MB');
  });
});

describe('DocumentsStore', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [DocumentsStore, provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: API }],
    });
    return { store: TestBed.inject(DocumentsStore), http: TestBed.inject(HttpTestingController) };
  }

  it('loads the documents, counts damage photos and fetches images with the token-carrying client', () => {
    const { store, http } = setup();
    store.load('c-1');
    http.expectOne(`${API}/api/claims/c-1/documents`).flush([
      doc('d-1'),
      doc('d-2', { category: 'POLICE_REPORT', categoryLabel: 'Police report', contentType: 'application/pdf' }),
    ]);

    expect(store.documents().length).toBe(2);
    expect(store.damagePhotoCount()).toBe(1);
    // only the image is downloaded for a thumbnail
    const thumb = http.expectOne(`${API}/api/claims/c-1/documents/d-1/content`);
    expect(thumb.request.responseType).toBe('blob');
    thumb.flush(new Blob(['x'], { type: 'image/jpeg' }));
    http.verify();
  });

  it('uploads valid files one after another and reports the invalid ones without sending them', () => {
    const { store, http } = setup();
    store.load('c-1');
    http.expectOne(`${API}/api/claims/c-1/documents`).flush([]);

    let uploaded = -1;
    store
      .upload('DAMAGE_PHOTO', [file('front.jpg', 'image/jpeg'), file('scan.pdf', 'application/pdf'), file('side.png', 'image/png')])
      .subscribe((n) => (uploaded = n));

    expect(store.uploadErrors()).toEqual([expect.stringContaining('scan.pdf is not a photo')]);
    const first = http.expectOne((r) => r.method === 'POST' && r.url === `${API}/api/claims/c-1/documents`);
    expect(first.request.params.get('category')).toBe('DAMAGE_PHOTO');
    expect((first.request.body as FormData).get('file')).toBeInstanceOf(File);
    expect(store.uploading()).toEqual(['front.jpg']);
    first.flush(doc('d-1'));
    http.expectOne(`${API}/api/claims/c-1/documents/d-1/content`).flush(new Blob());

    const second = http.expectOne((r) => r.method === 'POST');
    second.flush({ title: 'Unsupported file', detail: 'Only JPEG, PNG or WebP photos.' }, { status: 415, statusText: 'Unsupported' });

    expect(uploaded).toBe(1);
    expect(store.documents().map((d) => d.id)).toEqual(['d-1']);
    expect(store.uploadErrors()[1]).toBe('side.png: Only JPEG, PNG or WebP photos.');
    expect(store.uploading()).toEqual([]);
    http.verify();
  });

  it('opens images in the viewer', () => {
    const { store, http } = setup();
    store.load('c-1');
    http.expectOne(`${API}/api/claims/c-1/documents`).flush([]);
    store.open(doc('d-9'));
    expect(store.preview()?.id).toBe('d-9');
    store.closePreview();
    expect(store.preview()).toBeNull();
  });
});
