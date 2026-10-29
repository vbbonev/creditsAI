/** Attachment limits, mirroring the backend's rules (the backend has the final say). */
export const ATTACHMENT_LIMITS = {
  /** Maximum size of one file, in bytes (5 MB). */
  maxFileBytes: 5 * 1024 * 1024,
  /** Maximum total size of all files of one client or credit, in bytes (20 MB). */
  maxTotalBytes: 20 * 1024 * 1024,
  /** Maximum number of files per client or credit. */
  maxFiles: 30,
} as const;

/** Allowed file extensions (lower case, with the dot): documents first, then pictures. */
export const ALLOWED_EXTENSIONS = [
  '.pdf',
  '.docx',
  '.xlsx',
  '.odt',
  '.txt',
  '.jpg',
  '.jpeg',
  '.png',
  '.gif',
  '.webp',
  '.heic',
  '.tiff',
  '.tif',
] as const;

/** Allowed MIME types. */
export const ALLOWED_MIME_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.oasis.opendocument.text',
  'text/plain',
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
  'image/heic',
  'image/tiff',
] as const;

/** Value for the `accept` attribute of the file inputs. */
export const ATTACHMENT_ACCEPT = [...ALLOWED_EXTENSIONS, ...ALLOWED_MIME_TYPES].join(',');

/** Picture types every mainstream browser can display, so they get a thumbnail and a preview. */
export const PREVIEWABLE_MIME_TYPES: readonly string[] = [
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
];

/** Human-readable list of the allowed types, used in messages. */
export const ALLOWED_TYPES_TEXT = 'PDF, DOCX, XLSX, ODT, TXT, JPEG, PNG, GIF, WEBP, HEIC, TIFF';
