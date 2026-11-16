import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { Observable, concatMap, from, of } from 'rxjs';
import { catchError, finalize, map } from 'rxjs/operators';

import { ClaimsApi } from '../api/claims.api';
import { errorMessage } from '../errors/problem';
import { ClaimDocument, DOCUMENT_CATEGORIES, DOCUMENT_LIMITS, DocumentCategory } from '../models/api.models';

/** Client-side check before uploading, so the user hears about a wrong file at once. The server checks again. */
export function checkDocumentFile(file: File, category: DocumentCategory): string | null {
  const categoryInfo = DOCUMENT_CATEGORIES.find((c) => c.code === category);
  const allowed = categoryInfo?.imagesOnly ? DOCUMENT_LIMITS.imageTypes : DOCUMENT_LIMITS.allTypes;
  if (file.size === 0) {
    return `${file.name} is empty.`;
  }
  if (file.size > DOCUMENT_LIMITS.maxBytes) {
    return `${file.name} is ${formatBytes(file.size)}. The limit is 10 MB per file.`;
  }
  if (!allowed.includes(file.type)) {
    return categoryInfo?.imagesOnly
      ? `${file.name} is not a photo. Damage photos must be JPEG, PNG or WebP.`
      : `${file.name} is not a supported file. Upload JPEG, PNG or WebP photos, or PDF documents.`;
  }
  return null;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Photos and documents of one claim. Images are fetched as blobs with the bearer token and shown through object
 * URLs, which are revoked when the view is destroyed.
 */
@Injectable()
export class DocumentsStore {
  private readonly api = inject(ClaimsApi);
  private claimId: string | null = null;
  private readonly objectUrls = new Map<string, string>();

  readonly documents = signal<ClaimDocument[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** Names of files currently uploading. */
  readonly uploading = signal<string[]>([]);
  readonly uploadErrors = signal<string[]>([]);
  /** Document id -> object URL of the image, once loaded. */
  readonly thumbnails = signal<Record<string, string>>({});
  /** The image being looked at full size. */
  readonly preview = signal<ClaimDocument | null>(null);

  readonly damagePhotoCount = computed(() => this.documents().filter((d) => d.category === 'DAMAGE_PHOTO').length);
  readonly busy = computed(() => this.uploading().length > 0);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.revokeAll());
  }

  load(claimId: string): void {
    if (this.claimId !== claimId) {
      this.revokeAll();
      this.documents.set([]);
    }
    this.claimId = claimId;
    this.loading.set(true);
    this.error.set(null);
    this.api.documents(claimId).subscribe({
      next: (docs) => {
        this.documents.set(docs);
        this.loading.set(false);
        docs.filter(isImage).forEach((d) => this.loadThumbnail(d));
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  /** Uploads one file after another, so the order on screen is the order chosen. Returns how many succeeded. */
  upload(category: DocumentCategory, files: readonly File[]): Observable<number> {
    const claimId = this.claimId;
    this.uploadErrors.set([]);
    if (!claimId || files.length === 0) return of(0);

    const checked = files.map((file) => ({ file, problem: checkDocumentFile(file, category) }));
    const valid = checked.filter((c) => c.problem === null).map((c) => c.file);
    this.uploadErrors.set(checked.map((c) => c.problem).filter((p): p is string => p !== null));
    let succeeded = 0;

    return from(valid).pipe(
      concatMap((file) => {
        this.uploading.update((list) => [...list, file.name]);
        return this.api.uploadDocument(claimId, category, file).pipe(
          map((doc) => {
            succeeded++;
            this.documents.update((list) => [...list, doc]);
            if (isImage(doc)) this.loadThumbnail(doc);
          }),
          catchError((err: unknown) => {
            this.uploadErrors.update((list) => [...list, `${file.name}: ${errorMessage(err)}`]);
            return of(undefined);
          }),
          finalize(() => this.uploading.update((list) => list.filter((n) => n !== file.name))),
        );
      }),
      map(() => succeeded),
    );
  }

  /** Images open full size in the page; PDFs open in a new tab. */
  open(document: ClaimDocument): void {
    if (isImage(document)) {
      this.preview.set(document);
      return;
    }
    // open the tab inside the click, then point it at the file, so popup blockers allow it
    const tab = typeof window !== 'undefined' ? window.open('', '_blank') : null;
    this.api.documentContent(document).subscribe({
      next: (blob) => {
        const url = this.objectUrl(`file:${document.id}`, blob);
        if (tab && url) tab.location.href = url;
      },
      error: (err: unknown) => {
        tab?.close();
        this.error.set(errorMessage(err));
      },
    });
  }

  closePreview(): void {
    this.preview.set(null);
  }

  private loadThumbnail(document: ClaimDocument): void {
    if (this.thumbnails()[document.id]) return;
    this.api.documentContent(document).subscribe({
      next: (blob) => {
        const url = this.objectUrl(document.id, blob);
        if (url) this.thumbnails.update((t) => ({ ...t, [document.id]: url }));
      },
      error: () => {
        // the tile falls back to an icon
      },
    });
  }

  private objectUrl(key: string, blob: Blob): string | null {
    if (typeof URL.createObjectURL !== 'function') return null;
    const existing = this.objectUrls.get(key);
    if (existing) URL.revokeObjectURL(existing);
    const url = URL.createObjectURL(blob);
    this.objectUrls.set(key, url);
    return url;
  }

  private revokeAll(): void {
    if (typeof URL.revokeObjectURL === 'function') {
      this.objectUrls.forEach((url) => URL.revokeObjectURL(url));
    }
    this.objectUrls.clear();
    this.thumbnails.set({});
  }
}

function isImage(document: ClaimDocument): boolean {
  return document.contentType.startsWith('image/');
}
