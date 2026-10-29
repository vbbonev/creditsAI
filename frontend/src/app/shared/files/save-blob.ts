/**
 * Hands a downloaded file to the browser as a save action: creates a temporary object URL,
 * clicks an anchor with the `download` attribute and revokes the URL again.
 * @param blob the file content
 * @param fileName the name the browser proposes when saving
 */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}
