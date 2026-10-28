import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formats a byte count for people, e.g. 1536 as "1.5 KB".
 * @param bytes the size in bytes
 * @returns the size with a B, KB or MB unit
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const kb = bytes / 1024;
  if (kb < 1024) {
    return `${Number(kb.toFixed(1))} KB`;
  }
  return `${Number((kb / 1024).toFixed(1))} MB`;
}

/** Template pipe around {@link formatFileSize}. */
@Pipe({ name: 'fileSize' })
export class FileSizePipe implements PipeTransform {
  /**
   * Formats a byte count for people.
   * @param bytes the size in bytes
   * @returns the formatted size
   */
  transform(bytes: number): string {
    return formatFileSize(bytes);
  }
}
