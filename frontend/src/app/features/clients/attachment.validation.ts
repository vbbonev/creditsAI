import {
  ALLOWED_EXTENSIONS,
  ALLOWED_MIME_TYPES,
  ALLOWED_TYPES_TEXT,
  ATTACHMENT_LIMITS,
  PREVIEWABLE_MIME_TYPES,
} from './attachment.constants';
import { Attachment } from './attachment.model';
import { formatFileSize } from '../../shared/pipes/file-size.pipe';

/** The part of a `File` the validation needs. */
export type FileInfo = Pick<File, 'name' | 'size' | 'type'>;

/**
 * Checks whether a file has an allowed extension or MIME type (user experience only: the backend
 * checks the real content).
 * @param file the file to check
 * @returns true if the type is allowed
 */
export function isAllowedType(file: Pick<File, 'name' | 'type'>): boolean {
  const name = file.name.toLowerCase();
  return (
    ALLOWED_EXTENSIONS.some((extension) => name.endsWith(extension)) ||
    (ALLOWED_MIME_TYPES as readonly string[]).includes(file.type.toLowerCase())
  );
}

/**
 * Builds the key under which file names are compared: names are unique per client regardless of
 * case, and NFC-normalized like the backend does (macOS often supplies decomposed names).
 * @param name the file name
 * @returns the comparison key
 */
function nameKey(name: string): string {
  return name.normalize('NFC').toLowerCase();
}

/**
 * Checks selected files against the limits, together with the files the client already has.
 * @param files the newly selected files
 * @param existing the client's current attachments (empty when creating a client)
 * @returns readable messages, one per problem; empty when everything is fine
 */
export function validateFiles(
  files: readonly FileInfo[],
  existing: readonly Pick<Attachment, 'fileName' | 'sizeBytes'>[] = [],
): string[] {
  const errors: string[] = [];
  const seen = new Set(existing.map((a) => nameKey(a.fileName)));
  for (const file of files) {
    if (!isAllowedType(file)) {
      errors.push(`"${file.name}" has a type that is not allowed. Allowed: ${ALLOWED_TYPES_TEXT}.`);
    }
    if (file.size > ATTACHMENT_LIMITS.maxFileBytes) {
      errors.push(
        `"${file.name}" is larger than ${formatFileSize(ATTACHMENT_LIMITS.maxFileBytes)}.`,
      );
    }
    if (seen.has(nameKey(file.name))) {
      errors.push(`A file named "${file.name}" is already attached.`);
    }
    seen.add(nameKey(file.name));
  }
  if (existing.length + files.length > ATTACHMENT_LIMITS.maxFiles) {
    errors.push(`A client can have at most ${ATTACHMENT_LIMITS.maxFiles} files.`);
  }
  const total =
    existing.reduce((sum, a) => sum + a.sizeBytes, 0) + files.reduce((sum, f) => sum + f.size, 0);
  if (total > ATTACHMENT_LIMITS.maxTotalBytes) {
    errors.push(
      `All files of a client together can be at most ${formatFileSize(ATTACHMENT_LIMITS.maxTotalBytes)}.`,
    );
  }
  return errors;
}

/**
 * Checks whether the browser can show a picture of this type.
 * @param contentType the MIME type
 * @returns true for JPEG, PNG, GIF and WEBP
 */
export function isPreviewable(contentType: string): boolean {
  return PREVIEWABLE_MIME_TYPES.includes(contentType.toLowerCase());
}

/**
 * Picks the Material icon name for a file type.
 * @param contentType the MIME type
 * @returns the icon name
 */
export function fileIcon(contentType: string): string {
  const type = contentType.toLowerCase();
  if (type.startsWith('image/')) {
    return 'image';
  }
  if (type === 'application/pdf') {
    return 'picture_as_pdf';
  }
  if (type.includes('spreadsheet')) {
    return 'table_chart';
  }
  if (type === 'text/plain') {
    return 'article';
  }
  return 'description';
}
