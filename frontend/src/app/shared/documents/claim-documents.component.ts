import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { DOCUMENT_CATEGORIES, DOCUMENT_LIMITS, DocumentCategory } from '../models/api.models';
import { DocumentsStore, formatBytes } from './documents.store';

/**
 * Accident photos and supporting documents of one claim: a gallery, a full-size viewer and (when allowed) an
 * upload control. Used by the claimant app and the staff workspace; the server decides what each user sees.
 */
@Component({
  selector: 'icmp-claim-documents',
  imports: [DatePipe, FormsModule, MatButtonModule, MatFormFieldModule, MatProgressSpinnerModule, MatSelectModule],
  providers: [DocumentsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown.escape)': 'store.closePreview()' },
  template: `
    @if (canUpload()) {
      <div class="upload">
        <mat-form-field class="category" subscriptSizing="dynamic">
          <mat-label>Type of document</mat-label>
          <mat-select [ngModel]="category()" (ngModelChange)="category.set($event)" data-testid="document-category">
            @for (c of categories; track c.code) {
              <mat-option [value]="c.code">{{ c.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <input
          #fileInput
          type="file"
          class="file-input"
          multiple
          [accept]="accept()"
          (change)="filesChosen($event)"
          data-testid="document-file"
          aria-label="Choose files to upload"
        />
        <button mat-stroked-button type="button" (click)="fileInput.click()" [disabled]="store.busy()">
          <span class="material-symbols-outlined" aria-hidden="true">add_a_photo</span>
          {{ category() === 'DAMAGE_PHOTO' ? 'Add photos' : 'Add files' }}
        </button>
        <span class="icmp-muted hint">{{ hint() }}</span>
      </div>
    }

    @for (name of store.uploading(); track name) {
      <p class="progress" role="status"><mat-spinner diameter="16" aria-hidden="true" /> Uploading {{ name }}…</p>
    }
    @for (problem of store.uploadErrors(); track $index) {
      <p class="problem" role="alert">
        <span class="material-symbols-outlined" aria-hidden="true">error</span>{{ problem }}
      </p>
    }
    @if (store.error()) {
      <p class="problem" role="alert">
        <span class="material-symbols-outlined" aria-hidden="true">error</span>{{ store.error() }}
      </p>
    }

    @if (store.documents().length === 0 && !store.loading()) {
      <p class="icmp-muted empty">{{ emptyText() }}</p>
    }

    <ul class="gallery" aria-label="Photos and documents">
      @for (d of store.documents(); track d.id) {
        <li>
          <button type="button" class="tile" (click)="store.open(d)" [attr.aria-label]="'Open ' + d.fileName" data-testid="document-tile">
            @if (store.thumbnails()[d.id]; as src) {
              <img [src]="src" [alt]="d.categoryLabel + ': ' + d.fileName" />
            } @else {
              <span class="material-symbols-outlined icon" aria-hidden="true">
                {{ d.contentType === 'application/pdf' ? 'picture_as_pdf' : 'image' }}
              </span>
            }
          </button>
          <div class="caption">
            <strong>{{ d.categoryLabel }}</strong>
            <span class="name" [title]="d.fileName">{{ d.fileName }}</span>
            <span class="icmp-muted meta">
              {{ size(d.sizeBytes) }} · {{ d.uploadedAt | date: 'd MMM, HH:mm' }}
              @if (staffView()) {
                · {{ d.uploadedByRole === 'CLAIMANT' ? 'claimant' : d.uploadedByName }}
              }
            </span>
          </div>
        </li>
      }
    </ul>

    @if (store.preview(); as p) {
      <div class="viewer" role="dialog" aria-modal="true" [attr.aria-label]="p.fileName" (click)="store.closePreview()">
        <figure (click)="$event.stopPropagation()">
          <img [src]="store.thumbnails()[p.id]" [alt]="p.categoryLabel + ': ' + p.fileName" />
          <figcaption>
            <span>{{ p.categoryLabel }} · {{ p.fileName }}</span>
            <button mat-flat-button type="button" (click)="store.closePreview()">Close</button>
          </figcaption>
        </figure>
      </div>
    }
  `,
  styles: `
    :host { display: block; }
    .upload { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; margin-bottom: 12px; }
    .category { width: 200px; }
    .file-input { display: none; }
    .upload button .material-symbols-outlined { margin-right: 6px; font-size: 20px; }
    .hint { font-size: 0.8125rem; }
    .progress, .problem { display: flex; align-items: center; gap: 8px; margin: 4px 0; font-size: 0.875rem; }
    .problem { color: var(--icmp-tone-danger-fg); }
    .problem .material-symbols-outlined { font-size: 18px; }
    .empty { margin: 4px 0 0; }
    .gallery { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 12px; list-style: none; margin: 8px 0 0; padding: 0; }
    .tile { display: grid; place-items: center; width: 100%; aspect-ratio: 4 / 3; padding: 0; overflow: hidden; border: 1px solid var(--icmp-line); border-radius: 8px; background: var(--icmp-surface-2); cursor: zoom-in; }
    .tile:focus-visible { outline: 2px solid var(--icmp-accent); outline-offset: 2px; }
    .tile img { width: 100%; height: 100%; object-fit: cover; }
    .tile .icon { font-size: 40px; color: var(--icmp-muted); }
    .caption { display: grid; gap: 1px; margin-top: 6px; font-size: 0.8125rem; min-width: 0; }
    .name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .meta { font-size: 0.75rem; }
    .viewer { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; padding: 16px; background: rgb(0 0 0 / 0.8); }
    .viewer figure { display: grid; gap: 8px; margin: 0; max-width: min(1100px, 100%); }
    .viewer img { max-width: 100%; max-height: calc(100vh - 120px); object-fit: contain; border-radius: 6px; background: #fff; }
    .viewer figcaption { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; color: #fff; }
  `,
})
export class ClaimDocumentsComponent {
  protected readonly store = inject(DocumentsStore);
  private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

  readonly claimId = input.required<string>();
  readonly canUpload = input(false);
  /** Staff see who uploaded each file. */
  readonly staffView = input(false);
  readonly emptyText = input('No photos or documents yet.');
  /** Number of damage photos, whenever the list changes (staff use it to warn before approving). */
  readonly damagePhotos = output<number>();

  protected readonly categories = DOCUMENT_CATEGORIES;
  protected readonly category = signal<DocumentCategory>('DAMAGE_PHOTO');

  constructor() {
    effect(() => this.store.load(this.claimId()));
    effect(() => {
      const count = this.store.damagePhotoCount();
      if (!this.store.loading()) this.damagePhotos.emit(count);
    });
  }

  protected accept(): string {
    const info = DOCUMENT_CATEGORIES.find((c) => c.code === this.category());
    return (info?.imagesOnly ? DOCUMENT_LIMITS.imageTypes : DOCUMENT_LIMITS.allTypes).join(',');
  }

  protected hint(): string {
    return this.category() === 'DAMAGE_PHOTO'
      ? 'JPEG, PNG or WebP, up to 10 MB each. Show the damage clearly, from more than one angle.'
      : 'Photo or PDF, up to 10 MB each.';
  }

  protected filesChosen(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    this.store.upload(this.category(), files).subscribe();
    // allow choosing the same file again after a failure
    const element = this.fileInput()?.nativeElement;
    if (element) element.value = '';
  }

  protected size(bytes: number): string {
    return formatBytes(bytes);
  }
}
